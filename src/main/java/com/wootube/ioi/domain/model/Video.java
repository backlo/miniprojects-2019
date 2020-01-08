package com.wootube.ioi.domain.model;

import com.wootube.ioi.service.dto.VideoS3ConverterDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.*;

@Entity
@Getter
@NoArgsConstructor
@DynamicUpdate
public class Video extends BaseEntity {
    private static final int CONTENT_PATH = 0;
    private static final int THUMBNAIL_PATH = 1;

    @Column(nullable = false,
            length = 50)
    private String title;

    @Lob
    @Column(nullable = false)
    private String description;

    @Lob
    @Column(nullable = false)
    private String contentPath;

    @Lob
    @Column(nullable = false)
    private String thumbnailPath;

    @Lob
    @Column(nullable = false)
    private String originFileName;

    @Lob
    @Column(nullable = false)
    private String thumbnailFileName;

    @Column(columnDefinition = "bigint default 0")
    private long views;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_video_to_user"), nullable = false)
    private User writer;

    public Video(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void updateVideo(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void updateVideo(VideoS3ConverterDto videoS3ConverterDto) {
        this.contentPath = videoS3ConverterDto.getContentPath();
        this.originFileName = videoS3ConverterDto.getOriginFileName();
        this.thumbnailPath = videoS3ConverterDto.getThumbnailPath();
        this.thumbnailFileName = videoS3ConverterDto.getThumbnailFileName();
    }

    public void initialize(VideoS3ConverterDto videoS3ConverterDto, User writer) {
        this.contentPath = videoS3ConverterDto.getContentPath();
        this.originFileName = videoS3ConverterDto.getOriginFileName();
        this.thumbnailPath = videoS3ConverterDto.getThumbnailPath();
        this.thumbnailFileName = videoS3ConverterDto.getThumbnailFileName();
        this.writer = writer;
    }

    public boolean matchWriter(Long userId) {
        return writer.isSameEntity(userId);
    }

    public void increaseViews() {
        this.views++;
    }

}
