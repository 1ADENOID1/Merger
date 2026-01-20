import com.google.gson.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JsonMap {
    private final char separator = '█';

    private final String validationRegexp = "[a-zA-Zа-яА-ЯёЁ0-9_ !@#№$%^&?*(){}\\[\\]/\\\\|+=\\-~`,.:;'\"<>]+";

    private Map<String, JsonElement> jsonMap = new LinkedHashMap<>();

    public JsonMap(Map<String, JsonElement> map) {
        this.jsonMap = new LinkedHashMap<>(map);
    }

    public JsonMap(File inputFile) throws IOException {
        fromFile(inputFile);
    }

    public JsonMap(JsonElement elem) {
        fromJsonElement(elem);
    }

    public JsonMap(JsonMap anotherJsonMap) {
        this.jsonMap = new LinkedHashMap<>(anotherJsonMap.jsonMap);
    }


    private void fromJsonElement(JsonElement elem) {

        if (elem.isJsonObject()) {
            for (Map.Entry<String, JsonElement> i : elem.getAsJsonObject().asMap().entrySet()) {

                if (!i.getKey().matches(validationRegexp)) {
                    throw new JsonParseException("Invalid characters founded in key: " + i.getKey());
                }
                this.jsonMap.put(i.getKey(), i.getValue());

            }
        } else if (elem.isJsonArray()) {
            this.jsonMap.put(null, elem.getAsJsonArray());
        }

        expandJsonObjects();
    }

    private void fromFile(File inputFile) throws IOException {
        FileReader inputFileReader = new FileReader(inputFile);

        JsonElement inputJsonElement = JsonParser.parseReader(inputFileReader);

        fromJsonElement(inputJsonElement);
    }

    private void expandJsonObjects() {

        boolean hasObjects = true;
        while (hasObjects) {
            Map<String, JsonElement> addMap = new LinkedHashMap<>();
            List<String> deletedKeys = new ArrayList<>();
            hasObjects = false;

            for (Map.Entry<String, JsonElement> i : this.jsonMap.entrySet()) {
                if (i.getValue().isJsonObject() && !i.getValue().getAsJsonObject().isEmpty()) {
                    hasObjects = true;
                    deletedKeys.add(i.getKey());

                    for (Map.Entry<String, JsonElement> j : i.getValue().getAsJsonObject().entrySet()) {
                        if (!j.getKey().matches(validationRegexp)) {
                            throw new JsonParseException("Invalid characters founded in key: " + j.getKey());
                        }

                        addMap.put(i.getKey() + this.separator + j.getKey(), j.getValue());
                    }
                }
            }

            Map<String, JsonElement> copy = new LinkedHashMap<>(this.jsonMap);
            this.jsonMap = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> copyIndex : copy.entrySet()) {
                boolean foundedInDeleted = false;
                for (String deletedKeysIndex : deletedKeys) {

                    if (copyIndex.getKey().equals(deletedKeysIndex)) {

                        foundedInDeleted = true;

                        for (Map.Entry<String, JsonElement> addMapIndex : addMap.entrySet()) {

                            if (addMapIndex.getKey().startsWith(deletedKeysIndex)
                                    && (deletedKeysIndex.length() == addMapIndex.getKey().length()
                                    || addMapIndex.getKey().length() > deletedKeysIndex.length() &&
                                    addMapIndex.getKey().charAt(deletedKeysIndex.length()) == separator
                            )
                            ) {
                                this.jsonMap.put(addMapIndex.getKey(), addMapIndex.getValue());
                            }
                        }

                        break;
                    }
                }
                if (!foundedInDeleted) {
                    this.jsonMap.put(copyIndex.getKey(), copyIndex.getValue());
                }
            }
        }
    }

    public JsonElement toJson() {

        if (this.jsonMap.containsKey(null)) {
            if (this.jsonMap.size() != 1) {
                throw new JsonIOException("Json root is array, but json map length is not 1");
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            if (!this.jsonMap.get(null).isJsonArray()) {
                throw new JsonIOException("Json root is array, but null key value is not array: " + this.jsonMap.get(null));
            }
            return gson.toJsonTree(this.jsonMap.get(null).getAsJsonArray());
        }

        int depth = 0;

        for (Map.Entry<String, JsonElement> i : this.jsonMap.entrySet()) {

            int currentDepth = (int) (i.getKey().chars().filter(c -> c == this.separator).count());
            if (currentDepth > depth) {
                depth = currentDepth;
            }
        }

        Map<String, JsonElement> jsonCopy = new LinkedHashMap<>(this.jsonMap);

        do {
            ArrayList<String> usedObjects = new ArrayList<>();
            Map<String, JsonElement> addMap = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> element : jsonCopy.entrySet()) {

                if (element.getKey().chars().filter(c -> c == this.separator).count() == depth) {

                    int separatorPos = element.getKey().lastIndexOf(this.separator);
                    String objectName;
                    String keyWithoutObjectName;

                    if (separatorPos >= 0) {
                        objectName = element.getKey().substring(separatorPos + 1);
                        keyWithoutObjectName = element.getKey().substring(0, separatorPos);
                    } else {
                        objectName = element.getKey();
                        keyWithoutObjectName = element.getKey();
                    }

                    if (usedObjects.contains(keyWithoutObjectName)) {
                        JsonElement obj = addMap.get(keyWithoutObjectName);

                        obj.getAsJsonObject().add(objectName, element.getValue());
                        jsonCopy.replace(keyWithoutObjectName, obj);

                    } else {
                        if (separatorPos >= 0) {
                            usedObjects.add(keyWithoutObjectName);
                            JsonObject obj = new JsonObject();

                            obj.add(objectName, element.getValue());
                            addMap.put(keyWithoutObjectName, obj);
                        } else {
                            addMap.put(objectName, element.getValue());
                        }
                    }
                }
            }

            Map<String, JsonElement> copyJsonCopy = new LinkedHashMap<>(jsonCopy);
            jsonCopy = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> copyJsonCopyElement : copyJsonCopy.entrySet()) {

                boolean fromAddMap = false;

                for (Map.Entry<String, JsonElement> addMapElement : addMap.entrySet()) {

                    if (
                            copyJsonCopyElement.getKey().startsWith(addMapElement.getKey()) &&
                                    (
                                            copyJsonCopyElement.getKey().length() == addMapElement.getKey().length()
                                                    || (copyJsonCopyElement.getKey().length() > addMapElement.getKey().length()
                                                    && copyJsonCopyElement.getKey().charAt(addMapElement.getKey().length()) == separator
                                            )
                                    )
                    ) {
                        fromAddMap = true;
                        CharSequence cs = String.valueOf(this.separator);

                        if (!addMapElement.getKey().contains(cs)) {

                            jsonCopy.put(addMapElement.getKey(), addMapElement.getValue());
                        } else {
                            jsonCopy.put(addMapElement.getKey(), addMapElement.getValue());
                        }
                    }
                }

                if (!fromAddMap) {
                    jsonCopy.put(copyJsonCopyElement.getKey(), copyJsonCopyElement.getValue());
                }
            }

            depth--;
        } while (depth > 0);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJsonTree(jsonCopy);
    }

    public char getSeparator() {
        return this.separator;
    }

    public Map<String, JsonElement> getJsonMap() {
        return this.jsonMap;
    }


    public void changePropertyValue(String key, JsonElement value) {                            //Зарезервировано для версии с GUI
        this.jsonMap.replace(key, value);
        expandJsonObjects();
    }

    public void addProperty(String key, JsonElement value) {                                    //Зарезервировано для версии с GUI
        this.jsonMap.put(key, value);
        expandJsonObjects();

    }

    public void deleteProperty(String key) {                                                     //Зарезервировано для версии с GUI
        this.jsonMap.remove(key);
    }

    void printJsonMap() {                                                                        //Для отладки
        for (Map.Entry<String, JsonElement> i : this.jsonMap.entrySet()) {
            System.out.println("key: " + i.getKey() + " Value: " + i.getValue());
        }
    }
}
