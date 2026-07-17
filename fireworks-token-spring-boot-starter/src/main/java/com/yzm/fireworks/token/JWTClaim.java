package com.yzm.fireworks.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;


/**
 * @author JYuan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JWTClaim {

    /**
     * jwt的过期时间
     */
    private Date exp;

    /**
     * 生成jwt的时间
     */
    private Date iat;

    /**
     * jwt所面向的用户(某些场景下会被当作用户id，如Centrifugo就将token中的sub当作用户id)
     */
    @Builder.Default
    private String sub = Constant.AUTHOR;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 扩展字段
     */
    private Object ext;

}
