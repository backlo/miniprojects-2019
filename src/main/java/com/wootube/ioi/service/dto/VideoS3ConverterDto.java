package com.wootube.ioi.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VideoS3ConverterDto {
    private String contentPath;
    private String originFileName;
    private String thumbnailPath;
    private String thumbnailFileName;

    public VideoS3ConverterDto(String contentPath, String originFileName, String thumbnailPath, String thumbnailFileName) {
        this.contentPath = contentPath;
        this.originFileName = originFileName;
        this.thumbnailPath = thumbnailPath;
        this.thumbnailFileName = thumbnailFileName;
    }
}
