package com.moments;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;

@Configuration
public class GlobalConfig {
    @RequestMapping("/moments")  // This should not be empty
    public static class ApiRoot {
        // This class doesn't need methods, it's just a holder for the mapping
    }
}

