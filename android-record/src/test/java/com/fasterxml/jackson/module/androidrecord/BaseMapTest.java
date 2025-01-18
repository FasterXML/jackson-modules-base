package com.fasterxml.jackson.module.androidrecord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public abstract class BaseMapTest
    extends BaseTest
{
    protected static ObjectMapper newJsonMapper() {
        return JsonMapper.builder()
                .addModule(new AndroidRecordModule())
                .build();
    }
}
