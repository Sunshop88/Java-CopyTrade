package net.maku.subcontrol.config;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.serializer.ToStringSerializer;
import com.alibaba.fastjson.parser.deserializer.Jdk8DateCodec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class FastJsonConfiguration {

    @Bean
    public FastJsonConfig fastJsonConfig() {
        FastJsonConfig config = new FastJsonConfig();

        // 1. 注册 Java 8 时间模块支持
        ParserConfig parserConfig = ParserConfig.getGlobalInstance();
        SerializeConfig serializeConfig = SerializeConfig.getGlobalInstance();

        // 设置 LocalDateTime 的序列化和反序列化处理器
        parserConfig.putDeserializer(LocalDateTime.class, Jdk8DateCodec.instance);
        serializeConfig.put(LocalDateTime.class, Jdk8DateCodec.instance);

        // 设置其他序列化特性（可选，根据需求定制）
        config.setSerializerFeatures(
                SerializerFeature.WriteMapNullValue,        // 输出空字段
                SerializerFeature.WriteDateUseDateFormat,  // 日期格式化
                SerializerFeature.PrettyFormat             // 美化输出
        );

        // 绑定到配置
        config.setParserConfig(parserConfig);
        config.setSerializeConfig(serializeConfig);
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");
        return config;
    }
}

