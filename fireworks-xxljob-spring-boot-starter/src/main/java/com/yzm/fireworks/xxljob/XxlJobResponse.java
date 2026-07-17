package com.yzm.fireworks.xxljob;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author JYuan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class XxlJobResponse {
    private Integer code;
    private String msg;
    private String content;
}
