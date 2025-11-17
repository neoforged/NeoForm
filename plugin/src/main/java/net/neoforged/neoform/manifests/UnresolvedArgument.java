package net.neoforged.neoform.manifests;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.util.List;

public sealed interface UnresolvedArgument {
    JsonSerializer<UnresolvedArgument> JSON_SERIALIZER = (src, typeOfSrc, context) -> switch (src) {
        case ConditionalValue conditionalValue -> context.serialize(conditionalValue, ConditionalValue.class);
        case Value(String stringValue) -> new JsonPrimitive(stringValue);
        case null -> JsonNull.INSTANCE;
    };
    JsonDeserializer<UnresolvedArgument> JSON_DESERIALIZER = (json, typeOfT, context) -> {
        if (json.isJsonNull()) {
            return null;
        } else if (json.isJsonPrimitive()) {
            return new Value(json.getAsJsonPrimitive().getAsString());
        } else if (json.isJsonObject()) {
            var obj = json.getAsJsonObject().deepCopy();
            if (obj.has("value") && obj.get("value").isJsonPrimitive()) {
                var value = obj.remove("value");
                var values = new JsonArray();
                values.add(value);
                obj.add("value", values);
            }
            return context.deserialize(obj, ConditionalValue.class);
        }
        throw new JsonParseException("Expected string, null or object: " + json);
    };

    record Value(String value) implements UnresolvedArgument {
    }

    record ConditionalValue(List<String> value, List<Rule> rules) implements UnresolvedArgument {
    }
}
