package com.podman.qsy_videos.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private DataObj data;

    @Data
    @Builder
    public static class DataObj{
        private String video_title;
        private String video_url;
        private String download_url;
        private String image_url;
        private String uid;
        private String name;
        private String avatar_url;
        private List<String> images; // 新增图集字段
        private String platform;     // 新增平台来源字段
    }

}
