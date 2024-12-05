package tools.jackson.module.androidrecord;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public abstract class BaseMapTest
    extends BaseTest
{
    protected static ObjectMapper newJsonMapper() {
        return jsonMapperBuilder().build();
    }

    protected static JsonMapper.Builder jsonMapperBuilder() {
        return JsonMapper.builder()
                .addModule(new AndroidRecordModule());
    }
}
