package com.yzm.fireworks.id;

import com.tencent.devops.leaf.common.Result;
import com.tencent.devops.leaf.common.Status;
import com.tencent.devops.leaf.service.SegmentService;
import com.yzm.fireworks.common.util.ApplicationContextUtil;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * @author JYuan
 */
public class IdUtil {

    public static String getUUID() {
        return UUID.randomUUID().toString();
    }

    public static long getId(String key) {
        SegmentService segmentService = ApplicationContextUtil.getBean(SegmentService.class);
        Result result = segmentService.getId(key);
        Assert.isTrue(Status.SUCCESS.equals(result.getStatus()), "Failed to obtain the distributed id");
        return result.getId();
    }

    public static long getId(IdType idType) {
        return getId(idType.getValue());
    }
}
