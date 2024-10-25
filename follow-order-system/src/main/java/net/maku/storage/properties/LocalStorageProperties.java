package net.maku.storage.properties;

import lombok.Data;

/**
 * 本地存储配置项
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
public class LocalStorageProperties {
    /**
     * 本地存储路径
     */
    private String path;

    /**
     * 本地linux存储路径
     */
    private String linuxpath;

    /**
     * 资源起始路径
     */
    private String url = "upload";
}
