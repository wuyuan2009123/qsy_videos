package com.podman.qsy_videos.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    }

}
