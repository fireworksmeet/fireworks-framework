package com.yzm.fireworks.oplog.context;

import org.springframework.util.ObjectUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志上下文
 * <p>
 * 用于在业务方法执行期间传递数据给日志模板求值，业务通过
 * {@link #putVariable(String, Object)} 放入的变量，可在 {@code @LogRecord}
 * 的模板中以 {@code #变量名} 引用：
 *
 * <pre>{@code
 * @LogRecord(success = "将 #{#old} 改为 #{#user.name}")
 * public void update(User user) {
 *     // 在业务方法体内放入变量，此处能拿到方法执行前的旧值
 *     LogRecordContext.putVariable("old", userMapper.selectById(user.getId()));
 *     ...
 * }
 * }</pre>
 *
 * <h3>两类变量的区别</h3>
 * <ul>
 *     <li><b>方法级变量</b>（{@link #putVariable}）：保存在栈中，随方法调用层级压栈 / 出栈，
 *         作用域限定在当前被注解方法内，是推荐用法</li>
 *     <li><b>全局变量</b>（{@link #putGlobalVariable}）：不随方法出栈，
 *         作用域覆盖当前线程的后续所有日志求值，适用于框架级别的通用变量</li>
 * </ul>
 * 取值时方法级变量优先，方法级不存在才回退到全局变量。
 *
 * <h3>栈式设计</h3>
 * 变量表以 {@link Deque} 保存，支持<b>嵌套调用</b>：若方法 A 调用方法 B，
 * 且两者都标注了 {@code @LogRecord}，则 A、B 各自拥有独立的变量表，
 * B 中 {@code putVariable} 的变量不会污染 A 的求值。
 *
 * <h3>生命周期</h3>
 * 由 {@code LogRecordInterceptor} 在方法进入时通过 {@link #putEmptySpan()} 压栈，
 * 方法退出时通过 {@link #clear()} 出栈，业务无需手动管理。
 *
 * <h3>线程模型</h3>
 * 底层为 {@link InheritableThreadLocal}，变量按线程隔离。
 * <p>
 * <b>读写的全链路在同一线程内完成</b>，不存在跨线程读取，因此无需额外的
 * 「线程上下文传递」机制：
 *
 * <pre>{@code
 * 业务线程 T：
 *   putEmptySpan()      ← 压栈
 *   proceed()           ← 业务执行，业务在此调 putVariable
 *   processTemplate()   ← 拦截器在同一线程上读取变量并求值
 *   clear()             ← 出栈
 * }</pre>
 *
 * 由此可推出两个常见疑问的答案：
 * <ul>
 *     <li><b>异步落库无需传递上下文</b>：模板求值发生在业务线程，
 *         传给 {@code ILogRecordService} 的已是组装完毕的 {@code LogRecord} 对象，
 *         异步线程只处理该对象，不访问本上下文</li>
 *     <li><b>业务方法本身异步执行（如 {@code @Async}）也没有问题</b>：
 *         拦截器在新线程上执行，压栈、求值、出栈都在同一线程，天然自洽</li>
 * </ul>
 *
 * 需要注意的场景是：业务在<b>自己的子线程</b>中调用
 * {@link #putVariable(String, Object)}——由于线程池复用线程时不会重新继承父线程变量，
 * 该变量不会被本次求值读到，且可能残留到该线程的后续任务。此类场景应改用
 * 方法参数或 {@code #_result} 传递数据。
 *
 * @author muzhantong
 */
public class LogRecordContext {

    /**
     * 方法级变量表栈
     * <p>
     * 每个被注解的方法对应栈中一层，用于支持嵌套调用时的变量隔离。
     * 由 {@link #clear()} 在栈空时移除 ThreadLocal，避免线程池复用线程时的残留。
     */
    private static final InheritableThreadLocal<Deque<Map<String, Object>>> VARIABLE_MAP_STACK = new InheritableThreadLocal<>();

    /**
     * 全局变量表
     * <p>
     * 不随方法出栈，作用域覆盖当前线程后续的所有日志求值。
     * <p>
     * <b>惰性创建</b>：仅在业务调用 {@link #putGlobalVariable(String, Object)} 时创建，
     * 且由业务在合适的时机调用 {@link #clearGlobal()} 清理，
     * 避免"从未使用全局变量"的线程也长期持有一个空 {@link Map} 条目。
     */
    private static final InheritableThreadLocal<Map<String, Object>> GLOBAL_VARIABLE_MAP = new InheritableThreadLocal<>();

    private LogRecordContext() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 放入方法级变量
     * <p>
     * 变量保存在当前方法的变量表中，在 {@code @LogRecord} 模板中以
     * {@code #name} 引用。若变量表栈尚未初始化，会自动创建一层。
     * <p>
     * 同名变量会覆盖旧值；变量名不能以 {@code _} 开头，
     * 以免与框架保留变量（{@code #_result}、{@code #_exception}）冲突。
     *
     * @param name  变量名
     * @param value 变量值
     */
    public static void putVariable(String name, Object value) {
        if (VARIABLE_MAP_STACK.get() == null) {
            Deque<Map<String, Object>> stack = new ArrayDeque<>();
            VARIABLE_MAP_STACK.set(stack);
        }
        Deque<Map<String, Object>> mapStack = VARIABLE_MAP_STACK.get();
        if (mapStack.isEmpty()) {
            VARIABLE_MAP_STACK.get().push(new HashMap<>());
        }
        VARIABLE_MAP_STACK.get().element().put(name, value);
    }

    /**
     * 放入全局变量
     * <p>
     * 与 {@link #putVariable(String, Object)} 的区别：全局变量不随方法出栈，
     * 当前线程后续的所有日志求值都能引用到。
     * <p>
     * 取值时方法级变量优先，因此同名时全局变量会被方法级变量覆盖。
     *
     * @param name  变量名
     * @param value 变量值
     */
    public static void putGlobalVariable(String name, Object value) {
        if (GLOBAL_VARIABLE_MAP.get() == null) {
            GLOBAL_VARIABLE_MAP.set(new HashMap<>());
        }
        GLOBAL_VARIABLE_MAP.get().put(name, value);
    }

    /**
     * 获取方法级变量的值
     *
     * @param key 变量名
     * @return 变量值；变量不存在或栈未初始化时返回 {@code null}
     */
    public static Object getVariable(String key) {
        Map<String, Object> variableMap = peekVariableMap();
        return variableMap == null ? null : variableMap.get(key);
    }

    /**
     * 获取变量值，方法级优先，回退到全局
     * <p>
     * 查找顺序：方法级变量 → 全局变量 → {@code null}。
     * <p>
     * 仅当方法级变量<b>不存在</b>（值为 {@code null}）时才回退到全局变量。
     * 业务显式放入的空串、空集合等值会被视为有效取值，不再回退。
     *
     * @param key 变量名
     * @return 变量值；两处都不存在时返回 {@code null}
     */
    public static Object getMethodOrGlobal(String key) {
        Map<String, Object> variableMap = peekVariableMap();
        if (variableMap != null) {
            Object value = variableMap.get(key);
            if (value != null) {
                return value;
            }
        }
        Map<String, Object> globalMap = GLOBAL_VARIABLE_MAP.get();
        return ObjectUtils.isEmpty(globalMap) ? null : globalMap.get(key);
    }

    /**
     * 获取当前方法的变量表
     * <p>
     * 供日志求值时批量注入 SpEL 上下文使用。
     *
     * @return 方法级变量表；栈未初始化时返回<b>空 Map（非 null）</b>
     */
    public static Map<String, Object> getVariables() {
        Map<String, Object> variableMap = peekVariableMap();
        return variableMap == null ? new HashMap<>() : variableMap;
    }

    /**
     * 安全获取栈顶变量表
     * <p>
     * 业务在切面之外直接调用取值时栈可能尚未初始化，此处统一兜底，避免 NPE
     *
     * @return 栈顶变量表，不存在时返回 null
     */
    private static Map<String, Object> peekVariableMap() {
        Deque<Map<String, Object>> mapStack = VARIABLE_MAP_STACK.get();
        if (mapStack == null || mapStack.isEmpty()) {
            return null;
        }
        return mapStack.peek();
    }

    /**
     * 获取全局变量表
     *
     * @return 全局变量表；未初始化时返回 {@code null}
     */
    public static Map<String, Object> getGlobalVariableMap() {
        return GLOBAL_VARIABLE_MAP.get();
    }

    /**
     * 清理当前方法的变量表（出栈）
     * <p>
     * 由拦截器在方法执行结束时调用。栈空后移除 ThreadLocal，
     * 避免线程池场景下线程长期持有变量引用导致内存泄漏。
     */
    public static void clear() {
        Deque<Map<String, Object>> mapStack = VARIABLE_MAP_STACK.get();
        if (mapStack == null) {
            return;
        }
        if (!mapStack.isEmpty()) {
            mapStack.pop();
        }
        // 栈已空时移除 ThreadLocal，避免线程池场景下长期持有引用导致内存泄漏
        if (mapStack.isEmpty()) {
            VARIABLE_MAP_STACK.remove();
        }
    }

    /**
     * 清理全局变量表
     * <p>
     * 直接移除 ThreadLocal 而非仅清空内容，避免线程池场景下线程长期持有
     * <b>空 {@link Map} 条目</b>造成的残留。全局变量不随方法出栈，
     * 需由放入方在合适的时机主动清理（如 {@code finally} 块中）。
     * <p>
     * 清理后若再次调用 {@link #putGlobalVariable(String, Object)}，会按需重建。
     */
    public static void clearGlobal() {
        GLOBAL_VARIABLE_MAP.remove();
    }

    /**
     * 压入一层空变量表（进入方法）
     * <p>
     * <b>日志使用方无需调用</b>，由拦截器在方法执行前调用。
     * 每进入一个被注解的方法就初始化一个 span 压入栈中，
     * 方法执行结束后由 {@link #clear()} 弹出，以此支持嵌套调用。
     * <p>
     * 全局变量表<b>不在此处创建</b>，仅在业务调用
     * {@link #putGlobalVariable(String, Object)} 时按需惰性创建，
     * 避免"从未使用全局变量"的线程也长期持有一个空 Map 条目。
     */
    public static void putEmptySpan() {
        Deque<Map<String, Object>> mapStack = VARIABLE_MAP_STACK.get();
        if (mapStack == null) {
            Deque<Map<String, Object>> stack = new ArrayDeque<>();
            VARIABLE_MAP_STACK.set(stack);
        }
        VARIABLE_MAP_STACK.get().push(new HashMap<>());
    }
}
