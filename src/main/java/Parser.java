import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

class JsonMap {
    private final char separator = ' ';

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
        //this.jsonMap.putAll(elem.getAsJsonObject().asMap());
        if (elem.isJsonObject()) {
            for (Map.Entry<String, JsonElement> i : elem.getAsJsonObject().asMap().entrySet()) {
                this.jsonMap.put(i.getKey().replaceAll(String.valueOf(separator), "_"), i.getValue());
            }
        } else if (elem.isJsonArray()) {
            this.jsonMap.put(null, elem.getAsJsonArray());
        }

        /*for (Map.Entry<String, JsonElement> i : this.jsonMap.entrySet()) {
            if (i.getKey().contains(cs)) {
                throw new JsonParseException("Empty character was found in json field: " + i.getKey() + ": " + i.getValue());
            }
        }*/

        expandJsonObjects();
    }

    private void fromFile(File inputFile) throws IOException {
        FileReader inputFileReader = new FileReader(inputFile);

        JsonElement inputJsonElement = JsonParser.parseReader(inputFileReader);
        /*if (inputJsonElement.getAsJsonObject().isEmpty()) {
            throw new JsonParseException("Input file is empty");
        }*/

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
                        addMap.put(i.getKey() + this.separator + j.getKey().replaceAll(String.valueOf(separator), "_"), j.getValue());
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

            int currentDepth = (int)(i.getKey().chars().filter(c -> c == this.separator).count());
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
                        obj.getAsJsonObject().add(objectName.replaceAll("_", String.valueOf(this.separator)), element.getValue());
                        jsonCopy.replace(keyWithoutObjectName, obj);

                    } else {
                        if (separatorPos >= 0) {
                            usedObjects.add(keyWithoutObjectName);
                            JsonObject obj = new JsonObject();
                            obj.add(objectName.replaceAll("_", String.valueOf(this.separator)), element.getValue());
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
                            jsonCopy.put(addMapElement.getKey().replaceAll("_", String.valueOf(this.separator)), addMapElement.getValue());
                        } else {
                            jsonCopy.put(addMapElement.getKey(), addMapElement.getValue());
                        }
                    }
                }

                if (!fromAddMap) {
                    CharSequence cs = String.valueOf(this.separator);
                    if (!copyJsonCopyElement.getKey().contains(cs)) {
                        jsonCopy.put(copyJsonCopyElement.getKey().replaceAll("_", String.valueOf(this.separator)), copyJsonCopyElement.getValue());
                    } else {
                        jsonCopy.put(copyJsonCopyElement.getKey(), copyJsonCopyElement.getValue());
                    }
                }
            }

            depth--;
        } while (depth > 0);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJsonTree(jsonCopy);
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

class Merger {
    private final JsonMap userSettings;
    private final JsonMap defaultSettings;

    private JsonMap merged;


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
        }
        else {
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

                        //if (distrRootArrayFounded) {
                        mergedMap.put(null, userElem.getValue());
                        //}

                        userRootArrayFounded = true;
                        break;
                    }


                    if (!distrRootArrayFounded && (userElem.getKey().startsWith(defaultElem.getKey())
                            || defaultElem.getKey().startsWith(userElem.getKey()))) {   //Взятие значения из пользовательских файлов если оно задано (

                        foundedInUserElements = true;
                        mergedMap.put(userElem.getKey(), userElem.getValue());

                    }

                    for (Map.Entry<String, JsonElement> defaultElemNew : this.defaultSettings.getJsonMap().entrySet()) {               //Сохранение полей, отсутствующих в дистрибутиве
                        if (distrRootArrayFounded || !userElem.getKey().startsWith(defaultElemNew.getKey()) && !mergedMap.containsKey(userElem.getKey())) {
                            mergedMap.put(userElem.getKey(), userElem.getValue());
                        }
                    }
                }

                if (userRootArrayFounded || distrRootArrayFounded) {

                    break;
                }

                if (!foundedInUserElements) {                                               //Добавление новых полей из дистрибутива
                    mergedMap.put(defaultElem.getKey(), defaultElem.getValue());
                }
            }
        }

        this.merged = new JsonMap(mergedMap);
    }


    public JsonMap getMerged() {
        return this.merged;
    }
}

class FilePair {
    public String name;
    public JsonMap distrSettings;
    public JsonMap userSettings;

    public FilePair(String name, JsonMap distrSettings, JsonMap userSettings) {
        this.name = name;
        this.distrSettings = distrSettings;
        this.userSettings = userSettings;
    }
}

public class Parser {

    public static String getRelativePath(String baseDirectoryPath, String incFilePath) {
        Path basePath = Paths.get(baseDirectoryPath);
        Path incPath = Paths.get(incFilePath);
        return basePath.relativize(incPath).toString();
    }

    public static List<File> filesToList(File dir) {
        return filesToList(dir, true);
    }
    public static List<File> filesToList(File dir, boolean ignoreNotJsonFiles) {

        if (dir.listFiles() == null) {
            return new ArrayList<>();
        }

        List<File> expandedFileList = new ArrayList<>(Arrays.asList(dir.listFiles()));

        boolean hasDirs;
        do {
            hasDirs = false;

            ListIterator<File> distrExpandedFileListIterator = expandedFileList.listIterator();
            while (distrExpandedFileListIterator.hasNext()) {
                File file = distrExpandedFileListIterator.next();

                if (file.isDirectory()) {
                    hasDirs = true;

                    File[] includedFiles = file.listFiles();
                    distrExpandedFileListIterator.remove();

                    if (includedFiles != null) {
                        for (File i : includedFiles) {
                            distrExpandedFileListIterator.add(i);
                        }
                    }
                }
            }
        } while (hasDirs);

        if (ignoreNotJsonFiles) {
            expandedFileList.removeIf(file -> !file.getName().endsWith(".json"));
        }

        return new ArrayList<>(expandedFileList);
    }

    public static void main(String[] arg) throws IOException {

        if (arg.length < 5) {
            String output;
            switch (arg.length) {
                case 1: output="Default directory is not defined"; break;
                case 2: output="User directory is not defined"; break;
                case 3: output="Backup directory is not defined"; break;
                case 4: output="Parameter \"Create new directory for backup\" is not defined"; break;
                default: output="";
            }
            System.out.println(output);
            System.exit(1);
        }

        String distrDir = arg[1];
        String userDir = arg[2];
        String backupUserFiles = arg[3];

        if (!arg[4].equalsIgnoreCase("true") && !arg[4].equalsIgnoreCase("false")) {
            System.err.println("Parameter \"Create new directory for backup\" must be true or false");
            System.exit(1);
        }
        boolean createNewDirToBackup = Boolean.parseBoolean(arg[4]);

        String fileEncoding;
        if (arg.length < 6 || arg[5] == null) {
            fileEncoding = "UTF-8";
        } else {
            fileEncoding = arg[5];
        }
        System.out.println("Setup file encoding: " + fileEncoding);
        System.setProperty("file.encoding", fileEncoding);

        System.out.println("Distributive directory: " + distrDir);
        System.out.println("User directory: " + userDir);
        System.out.println("Backup directory: " + backupUserFiles);
        System.out.println("Backup mode: " + (createNewDirToBackup ? "Create subdirectory for backup" : "Backup to backup directory root"));

        File distr = new File(distrDir);
        File user = new File(userDir);
        File destFile = new File(backupUserFiles);

        if (!destFile.isDirectory()) {
           System.err.println("Backup path is not found or is not a directory");
           System.exit(1);
        }
        if (!createNewDirToBackup && destFile.list().length != 0) {
            System.err.println("Backup directory must be empty");
            System.exit(1);
        }
        else if (createNewDirToBackup) {

            String folderName = "Backup";
            destFile = new File(backupUserFiles += (File.separator + folderName));
            int i = 0;
            while (Files.exists(destFile.toPath())) {
                i++;
                destFile = new File(backupUserFiles + i);
            }

            Files.createDirectories(destFile.toPath());
        }

        if (!distr.isDirectory()) {
            System.err.println("Distributive path is not found or is not a directory");
            System.exit(1);
        }
        if (!user.isDirectory()) {
            System.err.println("User path is not found or is not a directory");
            System.exit(1);
        }


        List<File> distrExpandedFileList = filesToList(distr);
        List<File> userExpandedFileList = filesToList(user);

        Path destDir = destFile.toPath();

        List<File> filesToBackup = filesToList(user, false);
        System.out.println("Copying the user files to backup directory");
        try {
            for (File i : filesToBackup) {

                Files.createDirectories(destDir.resolve(getRelativePath(userDir, i.getAbsolutePath())));
                Files.copy(i.toPath(), destDir.resolve(getRelativePath(userDir, i.getAbsolutePath())), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exc) {
            System.err.println("Failed to backup user files. Stacktrace:");
            exc.printStackTrace();
            System.exit(1);
        }
        System.out.println("User files copied successfully");

        List<FilePair> parsedFiles = new ArrayList<>();

        System.out.println("Parsing the distributive and user files");
        try {
            for (File distrFile : distrExpandedFileList) {

                JsonMap distrFileJsonMap;
                JsonMap userFileJsonMap;
                boolean pairFounded = false;

                System.out.println("Parsing distributive file: " + distrFile.getAbsolutePath());
                distrFileJsonMap = new JsonMap(distrFile);

                String distrRelativeFilePath = getRelativePath(distrDir, distrFile.getAbsolutePath());

                for (File userFile : userExpandedFileList) {

                    String userRelativeFilePath = getRelativePath(userDir, userFile.getAbsolutePath());

                    if (distrRelativeFilePath.equals(userRelativeFilePath) && distrFile.isFile() && userFile.isFile()) {
                        pairFounded = true;

                        System.out.println("Parsing user file: " + user.getAbsolutePath());
                        userFileJsonMap = new JsonMap(userFile);
                        parsedFiles.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                        System.out.println("Parsed file pair: " + distrRelativeFilePath + " " + userRelativeFilePath);

                        break;
                    }
                }

                if (!pairFounded && distrFile.isFile()) {
                    System.out.println("Parsed distributive file (user file is not found, distributive file will be copied: " + distrRelativeFilePath);
                    userFileJsonMap = new JsonMap(distrFileJsonMap);
                    parsedFiles.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                }
            }
        } catch (IOException | JsonParseException ex) {
            System.err.println("Failed to parse file. Stacktrace:");
            ex.printStackTrace();
            System.exit(1);

        }

        System.out.println("Files parsed successfully");

        System.out.println("Merging the files");

        ListIterator<FilePair> parsedFilesIterator = parsedFiles.listIterator();
        while (parsedFilesIterator.hasNext()) {
            FilePair pair = parsedFilesIterator.next();
            System.out.println("Merging file pair: " + pair.name);
            Merger pairMerger = new Merger(pair.userSettings, pair.distrSettings);
            pair.userSettings = new JsonMap(pairMerger.getMerged());
            parsedFilesIterator.remove();
            parsedFilesIterator.add(pair);

        }

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        System.out.println("Files merged successfully");
        System.out.println("Writing the changes to user files");

        try {
            for (FilePair i : parsedFiles) {
                System.out.println("Writing " + i.name);
                File file = new File(userDir + File.separator + i.name);
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write(gson.toJson(i.userSettings.toJson()));
                fw.close();
            }
        } catch (IOException exc) {
            System.err.println("Failed to write file. Stacktrace:");
            exc.printStackTrace();
            System.exit(1);
        }

        System.out.println("Changes written successfully");
    }
}
