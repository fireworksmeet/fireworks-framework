package com.yzm.fireworks.storage.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageFile {

    private String bucketName;

    private String objectName;

    private String fileName;

    private String url;

    private Long size;

    private String contentType;

    private String etag;
}
