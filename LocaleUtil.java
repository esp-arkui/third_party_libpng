package com.huawei.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * @Description: localUtil
 * @ClassName: com.huawei.utils
 * @since: 2022/10/27  15:41
 **/
@Component
public class LocaleUtil {

    @Autowired
    private MessageSource messageSource;

    /**
     * 获取local message
     *
     * @param key 入参
     * @return message
     * */
    public String getMessage(String key){
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
