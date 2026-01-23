import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

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

        System.out.println("Checking the command line parameters...");

        if (arg.length < 6) {
            String output;
            switch (arg.length) {
                case 1: output="Parameter \"UPDATED_RES_DIR\" is not defined"; break;
                case 2: output="Parameter \"CURRENT_RES_DIR\" is not defined"; break;
                case 3: output="Parameter \"BACKUP_DIR\" is not defined"; break;
                case 4: output="Parameter \"CREATE_BACKUP_SUBDIR\" is not defined"; break;
                case 5: output="Parameter \"CREATE_CHANGELOG\" is not defined"; break;
                default: output="Count of received command line parameters is less that needed";
            }
            System.err.println("Error: " + output);
            System.exit(1);
        }

        String distrDir = arg[1];
        String userDir = arg[2];
        String backupUserFilesDir = arg[3];

        if (!arg[4].equalsIgnoreCase("true") && !arg[4].equalsIgnoreCase("false")) {
            System.err.println("Error: Parameter \"CREATE_BACKUP_SUBDIR\" must be true or false");
            System.exit(1);
        }
        boolean createNewDirToBackup = Boolean.parseBoolean(arg[4]);

        String fileEncoding;
        if (arg.length < 7 || arg[6] == null) {
            fileEncoding = "UTF-8";
        } else {
            fileEncoding = arg[6];
        }

        if (!arg[5].equalsIgnoreCase("true") && !arg[5].equalsIgnoreCase("false")) {
            System.err.println("Error: Parameter \"CREATE_CHANGELOG\" must be true or false");
            System.exit(1);
        }
        boolean logTheChanges = Boolean.parseBoolean(arg[5]);

        System.out.println("Setup file encoding: " + fileEncoding);
        System.setProperty("file.encoding", fileEncoding);

        System.out.println("Distributive directory: " + distrDir);
        System.out.println("User directory: " + userDir);
        System.out.println("Backup directory: " + backupUserFilesDir);
        System.out.println("Backup mode: " + (createNewDirToBackup ? "Create subdirectory for backup" : "Backup to directory root"));
        System.out.println("Writing file changelog: " + (logTheChanges ? "YES" : "NO"));

        File distrFileObjDir = new File(distrDir);
        File userFileObjDir = new File(userDir);
        File backupUserFileObjDir = new File(backupUserFilesDir);

        if (!backupUserFileObjDir.isDirectory()) {
           System.err.println("Error: Backup path is not found or is not a directory");
           System.exit(1);
        }
        if (!createNewDirToBackup && backupUserFileObjDir.list().length != 0) {
            System.err.println("Error: Backup directory must be empty");
            System.exit(1);
        }
        else if (createNewDirToBackup) {

            String backupFolderName = "Backup";
            backupUserFileObjDir = new File(backupUserFilesDir += (File.separator + backupFolderName));
            int i = 0;
            while (Files.exists(backupUserFileObjDir.toPath())) {
                i++;
                backupUserFileObjDir = new File(backupUserFilesDir + i);
            }

            Files.createDirectories(backupUserFileObjDir.toPath());
        }

        if (!distrFileObjDir.isDirectory()) {
            System.err.println("Error: Distributive path is not found or is not a directory");
            System.exit(1);
        }
        if (!userFileObjDir.isDirectory()) {
            System.err.println("Error: User path is not found or is not a directory");
            System.exit(1);
        }

        System.out.println("Checking the command line parameters is finished");

        List<File> distrExpandedFileList = filesToList(distrFileObjDir);
        List<File> userExpandedFileList = filesToList(userFileObjDir);

        Path destDir = backupUserFileObjDir.toPath();

        List<File> filesToBackup = filesToList(userFileObjDir, false);
        System.out.println("\nSTAGE 1: Copying the user files to backup directory");
        try {
            for (File i : filesToBackup) {

                Files.createDirectories(destDir.resolve(getRelativePath(userDir, i.getAbsolutePath())));
                Files.copy(i.toPath(), destDir.resolve(getRelativePath(userDir, i.getAbsolutePath())), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exc) {
            System.err.println("Error: Failed to backup user files. Stacktrace:");
            exc.printStackTrace();
            System.exit(1);
        }
        System.out.println("STAGE 1 FINISHED: User files copied successfully\n");

        List<FilePair> parsedFilePairList = new ArrayList<>();

        System.out.println("STAGE 2: Parsing the distributive and user files");
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

                        System.out.println("Parsing user file: " + userFile.getAbsolutePath());
                        userFileJsonMap = new JsonMap(userFile);
                        parsedFilePairList.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                        System.out.println("Parsed file pair: " + distrRelativeFilePath + " " + userRelativeFilePath);

                        break;
                    }
                }

                if (!pairFounded && distrFile.isFile()) {
                    System.out.println("Parsed distributive file (user file is not found, distributive file will be copied: " + distrRelativeFilePath);
                    userFileJsonMap = new JsonMap(distrFileJsonMap);
                    parsedFilePairList.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                }
            }
        } catch (IOException | JsonParseException ex) {
            System.err.println("Error: Failed to parse file. Stacktrace:");
            ex.printStackTrace();
            System.exit(1);

        }

        System.out.println("STAGE 2 FINISHED: Files parsed successfully\n");

        System.out.println("STAGE 3: Merging the files");

        Map<String, JsonMap> mergeResultsMap = new LinkedHashMap<>();

        Map<String, List<String>> fileChangesMap = new LinkedHashMap<>();

        for (FilePair pair : parsedFilePairList) {
            System.out.println("Merging file pair: " + pair.name);
            Merger filePairMerger = new Merger(pair.userSettings, pair.distrSettings);
            fileChangesMap.put(pair.name, filePairMerger.getChanges());
            mergeResultsMap.put(pair.name, new JsonMap(filePairMerger.getMerged()));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        System.out.println("STAGE 3 FINISHED: Files merged successfully\n");
        System.out.println("STAGE 4: Writing the changes to user files");

        try {
            for (Map.Entry<String, JsonMap> resultJsonMapWithFileName : mergeResultsMap.entrySet()) {
                System.out.println("Writing " + resultJsonMapWithFileName.getKey());
                File file = new File(userDir + File.separator + resultJsonMapWithFileName.getKey());
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write(gson.toJson(resultJsonMapWithFileName.getValue().toJson()));
                fw.close();
            }
        } catch (IOException | JsonIOException exc) {
            System.err.println("Error: Failed to write file. Stacktrace:");
            exc.printStackTrace();
            System.exit(1);
        }

        System.out.println("STAGE 4 FINISHED: Files written successfully\n");

        if (logTheChanges) {
            System.out.println("Saving changelog");
            File changelogFile = new File("changelog.txt");
            try (FileWriter fw = new FileWriter(changelogFile)) {

                for (Map.Entry<String, List<String>> i : fileChangesMap.entrySet()) {

                    if (!i.getValue().isEmpty()) {
                        fw.write("\nFile: " + i.getKey() + "\n");

                        for (String change : i.getValue()) {
                            fw.write(change + "\n");
                        }
                    }
                    fw.flush();
                }

                fw.close();
                System.out.println("Changelog saved");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
