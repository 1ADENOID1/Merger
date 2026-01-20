import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class Merger {
    private final JsonMap userSettings;
    private final JsonMap defaultSettings;

    private JsonMap merged;

    private List<String> changes = new ArrayList<>();


    public Merger(JsonMap userSettings, JsonMap defaultSettings) {
        this.userSettings = userSettings;
        this.defaultSettings = defaultSettings;

        merge();
    }

    protected void merge() {

        Map<String, JsonElement> mergedMap = new LinkedHashMap<>();

        boolean userRootArrayFounded = false;
        boolean distrRootArrayFounded = false;


        if (this.defaultSettings.getJsonMap().size() == 0) {           //Если defaultSettings пуст, то цикл не откроется
            mergedMap.putAll(this.userSettings.getJsonMap());
            this.changes.add("Default settings is empty. Saved all user settings");
        } else {
            for (Map.Entry<String, JsonElement> defaultElem : this.defaultSettings.getJsonMap().entrySet()) {
                boolean foundedInUserElements = false;

                if (defaultElem.getKey() == null) {
                    if (this.defaultSettings.getJsonMap().size() != 1) {
                        throw new JsonParseException("Json root is array, but json map length is not 1");
                    }

                    distrRootArrayFounded = true;

                }
                for (Map.Entry<String, JsonElement> userElem : this.userSettings.getJsonMap().entrySet()) {

                    if (userElem.getKey() == null) {
                        if (this.userSettings.getJsonMap().size() != 1) {
                            throw new JsonParseException("Json root is array, but json map length is not 1");
                        }

                        mergedMap.put(null, userElem.getValue());

                        if (distrRootArrayFounded && !userElem.getValue().equals(defaultElem.getValue())) {
                            this.changes.add("Value of root array in default and user file is different. Saved user value: " + userElem.getValue());
                        } else if (!distrRootArrayFounded) {
                            this.changes.add("Root element in default file is object and root element in user file is array. Saved user value: " + userElem.getValue());
                        }

                        userRootArrayFounded = true;
                        break;
                    }
                    if (defaultElem.getKey() == null) {
                        this.changes.add("Root element in default file is array and root element in user file is object. Saved user value: " + userElem.getValue());
                    }

                    if (!distrRootArrayFounded && ((userElem.getKey().startsWith(defaultElem.getKey())
                            && (userElem.getKey().length() == defaultElem.getKey().length() || userElem.getKey().charAt(defaultElem.getKey().length()) == this.userSettings.getSeparator())
                    )
                            || (defaultElem.getKey().startsWith(userElem.getKey())
                            && (defaultElem.getKey().length() == userElem.getKey().length() || defaultElem.getKey().charAt(userElem.getKey().length()) == this.userSettings.getSeparator())
                    )

                    )) {   //Взятие значения из пользовательских файлов если оно задано (

                        foundedInUserElements = true;
                        mergedMap.put(userElem.getKey(), userElem.getValue());

                        if (!userElem.getValue().equals(defaultElem.getValue())) {
                            this.changes.add("Default value overwritten by user value. Key: \"" + userElem.getKey() + "\" Saved value: " + userElem.getValue());
                        }
                    }


                }

                if (userRootArrayFounded || distrRootArrayFounded) {

                    break;
                }

                if (!foundedInUserElements) {                                               //Добавление новых полей из дистрибутива
                    mergedMap.put(defaultElem.getKey(), defaultElem.getValue());
                    this.changes.add("Default key: \"" + defaultElem.getKey() + "\" is not found in user file. Saved default value: " + defaultElem.getValue());
                }
            }

            for (Map.Entry<String, JsonElement> userElemNew : this.userSettings.getJsonMap().entrySet()) {
                for (Map.Entry<String, JsonElement> defaultElemNew : this.defaultSettings.getJsonMap().entrySet()) {               //Сохранение полей, отсутствующих в дистрибутиве
                    if (!userRootArrayFounded &&
                            (distrRootArrayFounded || !userElemNew.getKey().startsWith(defaultElemNew.getKey()) && !mergedMap.containsKey(userElemNew.getKey()))
                    ) {
                        mergedMap.put(userElemNew.getKey(), userElemNew.getValue());
                        this.changes.add("User key: \"" + userElemNew.getKey() + "\" is not found in default file. Saved user value: " + userElemNew.getValue());
                    }
                }
            }
        }


        this.merged = new JsonMap(mergedMap);
    }


    public JsonMap getMerged() {
        return this.merged;
    }

    public List<String> getChanges() {
        return this.changes;
    }
}
