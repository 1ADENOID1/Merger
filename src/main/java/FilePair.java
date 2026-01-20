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
