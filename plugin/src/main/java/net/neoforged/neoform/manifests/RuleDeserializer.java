package net.neoforged.neoform.manifests;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class RuleDeserializer implements JsonDeserializer<Rule> {
    @Override
    public Rule deserialize(JsonElement jsonElement, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!jsonElement.isJsonObject()) {
            throw new JsonParseException("Rule must be an object: " + jsonElement);
        }

        final JsonObject payload = jsonElement.getAsJsonObject();

        RuleAction action = context.deserialize(payload.get("action"), RuleAction.class);
        Type featuresType = new TypeToken<Map<String, Boolean>>() {
        }.getType();
        Map<String, Boolean> features = context.deserialize(payload.get("features"), featuresType);
        OsCondition os = context.deserialize(payload.get("os"), OsCondition.class);

        return new Rule(action, features, os);
    }
}
