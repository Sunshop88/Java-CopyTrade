package net.maku.system.controller;

import cn.hutool.core.thread.ThreadUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.util.ServersDatIniUtil;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.storage.properties.StorageProperties;
import net.maku.storage.service.StorageService;
import net.maku.system.vo.SysFileUploadVO;
import org.postgresql.util.OSUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件上传
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("sys/file")
@Tag(name = "文件上传")
@AllArgsConstructor
public class SysFileUploadController {
    private final StorageService storageService;
    public StorageProperties properties;
    private final ServersDatIniUtil serversDatIniUtil;


    @PostMapping("upload")
    @Operation(summary = "上传")
    @OperateLog(type = OperateTypeEnum.INSERT)
    public Result<SysFileUploadVO> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.error("请选择需要上传的文件");
        }

        // 上传路径
        String path = storageService.getPath(file.getOriginalFilename());
        // 上传文件
        String url = storageService.upload(file.getBytes(), path);

        SysFileUploadVO vo = new SysFileUploadVO();
        vo.setUrl(url);
        vo.setSize(file.getSize());
        vo.setName(file.getOriginalFilename());
        vo.setPlatform(storageService.properties.getConfig().getType().name());

        //如果上传为service.ini文件，异步处理
        if (file.getOriginalFilename().contains(".ini")){
            // 定义正则表达式，匹配 "YYYYMMDD/任意文件名.后缀" 的部分
            String regex = "\\d{8}/[^/]+\\.ini";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                String result1 = matcher.group();
                //1秒后执行
                ThreadPoolUtils.getExecutor().execute(()->{
                    try {
                        Thread.sleep(1000);
                        String uppath= OSUtil.isWindows()?properties.getLocal().getPath():properties.getLocal().getLinuxpath();
                        try {
                            serversDatIniUtil.ExportServersIni(uppath+"/"+result1);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return Result.ok(vo);
        }
        return Result.ok(vo);
    }

    @PostMapping("uploads")
    @Operation(summary = "上传")
    @OperateLog(type = OperateTypeEnum.INSERT)
    public SysFileUploadVO uploads(@RequestParam(value = "file", required = false) MultipartFile file) throws Exception {
        SysFileUploadVO vo = new SysFileUploadVO();
        if (file.isEmpty()) {
//            throw new ServerException("请选择需要上传的文件");
            return vo;
        }
        String contentType = file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new ServerException("上传的文件必须为图片格式");
        }
        // 检查文件大小
        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new ServerException("上传的文件大小不能超过 10MB");
        }
        // 上传路径
        String path = storageService.getPath(file.getOriginalFilename());
        // 上传文件
        String url = storageService.upload(file.getBytes(), path);

        vo.setUrl(url);
        vo.setName(file.getOriginalFilename());

        return vo;
    }
}
