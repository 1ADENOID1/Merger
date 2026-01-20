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

        if (arg.length < 6) {
            String output;
            switch (arg.length) {
                case 1: output="Default directory is not defined"; break;
                case 2: output="User directory is not defined"; break;
                case 3: output="Backup directory is not defined"; break;
                case 4: output="Parameter \"Create new directory for backup\" is not defined"; break;
                case 5: output="Parameter \"Create ChangeLog file\" is not defined"; break;
                default: output="Invalid arguments";
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
        if (arg.length < 7 || arg[6] == null) {
            fileEncoding = "UTF-8";
        } else {
            fileEncoding = arg[6];
        }
        boolean logTheChanges = Boolean.parseBoolean(arg[5]);
        System.out.println("Setup file encoding: " + fileEncoding);
        System.setProperty("file.encoding", fileEncoding);

        System.out.println("Distributive directory: " + distrDir);
        System.out.println("User directory: " + userDir);
        System.out.println("Backup directory: " + backupUserFiles);
        System.out.println("Backup mode: " + (createNewDirToBackup ? "Create subdirectory for backup" : "Backup to backup directory root"));
        System.out.println("Writing file changelog: " + (logTheChanges ? "YES" : "NO"));

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

        List<FilePair> parsedFilePairs = new ArrayList<>();

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

                        System.out.println("Parsing user file: " + userFile.getAbsolutePath());
                        userFileJsonMap = new JsonMap(userFile);
                        parsedFilePairs.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                        System.out.println("Parsed file pair: " + distrRelativeFilePath + " " + userRelativeFilePath);

                        break;
                    }
                }

                if (!pairFounded && distrFile.isFile()) {
                    System.out.println("Parsed distributive file (user file is not found, distributive file will be copied: " + distrRelativeFilePath);
                    userFileJsonMap = new JsonMap(distrFileJsonMap);
                    parsedFilePairs.add(new FilePair(distrRelativeFilePath, distrFileJsonMap, userFileJsonMap));
                }
            }
        } catch (IOException | JsonParseException ex) {
            System.err.println("Failed to parse file. Stacktrace:");
            ex.printStackTrace();
            System.exit(1);

        }

        System.out.println("Files parsed successfully");

        System.out.println("Merging the files");

        Map<String, JsonMap> mergeResults = new LinkedHashMap<>();

        Map<String, List<String>> fileChanges = new LinkedHashMap<>();

        for (FilePair pair : parsedFilePairs) {
            System.out.println("Merging file pair: " + pair.name);
            Merger filePairMerger = new Merger(pair.userSettings, pair.distrSettings);
            fileChanges.put(pair.name, filePairMerger.getChanges());
            mergeResults.put(pair.name, new JsonMap(filePairMerger.getMerged()));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        System.out.println("Files merged successfully");
        System.out.println("Writing the changes to user files");

        try {
            for (Map.Entry<String, JsonMap> resultJsonMapWithFileName : mergeResults.entrySet()) {
                System.out.println("Writing " + resultJsonMapWithFileName.getKey());
                File file = new File(userDir + File.separator + resultJsonMapWithFileName.getKey());
                file.getParentFile().mkdirs();
                FileWriter fw = new FileWriter(file);
                fw.write(gson.toJson(resultJsonMapWithFileName.getValue().toJson()));
                fw.close();
            }
        } catch (IOException | JsonIOException exc) {
            System.err.println("Failed to write file. Stacktrace:");
            exc.printStackTrace();
            System.exit(1);
        }

        System.out.println("Files written successfully");

        if (logTheChanges) {
            System.out.println("Saving changelog");
            File changelogFile = new File("changelog.txt");
            try (FileWriter fw = new FileWriter(changelogFile)) {

                for (Map.Entry<String, List<String>> i : fileChanges.entrySet()) {

                    fw.write("\nFile: " + i.getKey() + "\n");

                    if (!i.getValue().isEmpty()) {
                        for (String change : i.getValue()) {
                            fw.write(change + "\n");
                        }
                    } else {
                        fw.write("No changes in this file\n");
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
