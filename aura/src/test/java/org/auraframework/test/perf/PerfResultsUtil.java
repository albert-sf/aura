/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.test.perf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.auraframework.test.perf.metrics.PerfMetrics;
import org.auraframework.util.IOUtil;
import org.auraframework.util.test.PerfGoldFilesUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;

/**
 * Utility methods related to the results generated by the perf runs.
 */
public final class PerfResultsUtil {

    private static final Logger LOG = Logger.getLogger(PerfResultsUtil.class.getSimpleName());

    public static final File RESULTS_DIR;

    static {
        // use aura.perf.results.dir if set
        String resultsPath = System.getProperty("aura.perf.results.dir", null);

        // else use aura-integration-test/target/perf/results if running in aura
        if (resultsPath == null && new File("../aura-integration-test").exists()) {
            resultsPath = "../aura-integration-test/target/perf/results";
            try {
                resultsPath = new File(resultsPath).getCanonicalPath();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "error canonicalizing " + resultsPath, e);
            }
        }

        // else target/perf/results
        if (resultsPath == null) {
            resultsPath = "target/perf/results";
        }

        RESULTS_DIR = new File(resultsPath);
        LOG.info("perf results dir: " + RESULTS_DIR.getAbsolutePath());
        RESULTS_DIR.mkdirs();
    }

    public enum PerformanceMetrics {
        AURA_STATS("aurastats"),
        GOLD_FILES("goldfiles"),
        HEAPS("heaps", ".heapsnapshot"),
        PROFILES("profiles", ".cpuprofile"),
        TIMELINES("timelines");

        private final String value;
        private final String fileExtension;

        private PerformanceMetrics(String value) {
            this(value, ".json");
        }

        private PerformanceMetrics(String value, String fileExtension) {
            this.value = value;
            this.fileExtension = fileExtension;
        }

        public File getFile(String fileName) {
            File dir = new File(RESULTS_DIR, value);
            return new File(dir, fileName + fileExtension);
        }

        public static PerformanceMetrics getPerformanceMetricsFromType(String metricsType) {
            for (PerformanceMetrics result : PerformanceMetrics.values()) {
                if (result.value.equalsIgnoreCase(metricsType)) {
                    return result;
                }
            }
            throw new IllegalArgumentException("unknown metricsType: " + metricsType);
        }
    }

    /**
     * @return the written file
     */
    public static File writeGoldFile(PerfMetrics metrics, String fileName, boolean storeDetails) {
        File file = PerformanceMetrics.GOLD_FILES.getFile(fileName);
        RESULTS_JSON.addResultsFile(file);
        try {
            ALL_GOLDFILES_JSON.addGoldfile(fileName, metrics);
        } catch (JSONException e) {
            LOG.log(Level.WARNING, "error generating _all.json", e);
        }
        return writeFile(file, PerfGoldFilesUtil.toGoldFileText(metrics, storeDetails), "goldfile");
    }

    /**
     * @return the written file
     */
    public static File writeAuraStats(String auraStatsContents, String fileName) {
        File file = PerformanceMetrics.AURA_STATS.getFile(fileName);
        RESULTS_JSON.addResultsFile(file);
        return writeFile(file, auraStatsContents, "Aura Stats");
    }

    private static File writeFile(File file, String contents, String what) {
        OutputStreamWriter writer = null;
        try {
            IOUtil.mkdirs(file.getParentFile());
            writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(contents);
            LOG.info("wrote " + what + ": " + file.getAbsolutePath());
            return file;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "error writing " + file.getAbsolutePath(), e);
            return file;
        } finally {
            IOUtil.close(writer);
        }
    }

    /**
     * Writes the dev tools log for a perf test run to
     * System.getProperty("aura.perf.results.dir")/timelines/testName_timeline.json
     * 
     * @return the written file
     */
    public static File writeDevToolsLog(List<JSONObject> timeline, String fileName, String userAgent) {
        File file = PerformanceMetrics.TIMELINES.getFile(fileName);
        try {
            writeDevToolsLog(timeline, file, userAgent);
            RESULTS_JSON.addResultsFile(file);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "error writing " + file.getAbsolutePath(), e);
        }
        return file;
    }

    private static void writeDevToolsLog(List<JSONObject> timeline, File file, String userAgent) throws Exception {
        BufferedWriter writer = null;
        try {
            file.getParentFile().mkdirs();
            writer = new BufferedWriter(new FileWriter(file));
            writer.write('[');
            writer.write(JSONObject.quote(userAgent));
            for (JSONObject entry : timeline) {
                writer.write(',');
                writer.newLine();
                writer.write(entry.toString());
            }
            writer.write("]");
            writer.newLine();
            LOG.info("wrote dev tools log: " + file.getAbsolutePath());
        } finally {
            IOUtil.close(writer);
        }
    }

    /**
     * Writes the JavaScript CPU profile data for a perf test run to
     * System.getProperty("aura.perf.results.dir")/profiles/testName_profile.cpuprofile
     * 
     * @return the written file
     */
    public static File writeJSProfilerData(Map<String, ?> jsProfilerData, String fileName) {
        File file = PerformanceMetrics.PROFILES.getFile(fileName);
        try {
            file.getParentFile().mkdirs();
            BufferedWriter writer = null;
            try {
                FileOutputStream out = new FileOutputStream(file);
                writer = new BufferedWriter(new OutputStreamWriter(out, Charsets.US_ASCII));
                writer.write(new JSONObject(jsProfilerData).toString());
                RESULTS_JSON.addResultsFile(file);
                LOG.info("wrote JavaScript CPU profile: " + file.getAbsolutePath());
            } finally {
                IOUtil.close(writer);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "error writing " + file.getAbsolutePath(), e);
        }
        return file;
    }

    // JS heap snapshot

    /**
     * Writes the heap snapshot into a file, this file can be loaded into chrome dev tools -> Profiles -> Load
     * 
     * @return the written file
     */
    @SuppressWarnings("unchecked")
    public static File writeHeapSnapshot(Map<String, ?> data, String fileName) throws Exception {
        File file = PerformanceMetrics.HEAPS.getFile(fileName);
        BufferedWriter writer = null;
        try {
            file.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream(file);

            // write using same format as CDT Save:
            // https://developers.google.com/chrome-developer-tools/docs/heap-profiling
            writer = new BufferedWriter(new OutputStreamWriter(out, Charsets.US_ASCII));
            writer.write('{');
            writer.write(JSONObject.quote("snapshot"));
            writer.write(':');
            new JSONObject((Map<String, ?>) data.get("snapshot")).write(writer);
            writer.write(',');
            writer.newLine();
            writeList(writer, "nodes", (List<?>) data.get("nodes"), 5, false);
            writeList(writer, "edges", (List<?>) data.get("edges"), 3, false);
            writeList(writer, "trace_function_infos", (List<?>) data.get("trace_function_infos"), 1, false);
            writeList(writer, "trace_tree", (List<?>) data.get("trace_tree"), 1, false);
            writeList(writer, "strings", (List<?>) data.get("strings"), 1, true);
            writer.write('}');

            RESULTS_JSON.addResultsFile(file);
            LOG.info("wrote heap snapshot: " + file.getAbsolutePath());
        } finally {
            IOUtil.close(writer);
        }
        return file;
    }

    static void writeList(BufferedWriter writer, String key, List<?> list, int numPerLine, boolean last)
            throws IOException {
        writer.write(JSONObject.quote(key));
        writer.write(':');
        writer.write('[');
        for (int i = 0; i < list.size(); i++) {
            Object entry = list.get(i);
            if (i > 0) {
                if (numPerLine > 1) {
                    if (i % numPerLine == 0) {
                        writer.newLine();
                    }
                    writer.write(',');
                } else {
                    writer.write(',');
                    writer.newLine();
                }
            }
            if (entry instanceof String) {
                writer.write(JSONObject.quote((String) entry));
            } else {
                writer.write(entry.toString());
            }
        }
        if (numPerLine > 1) {
            writer.newLine();
        }
        writer.write("]");
        if (!last) {
            writer.write(',');
            writer.newLine();
        }
    }

    // generate Results.json:

    public static final ResultsJSON RESULTS_JSON = new ResultsJSON(true);

    /**
     * Generates a Results.json file pointing to all the artifacts generated in a perf test run.
     */
    public static final class ResultsJSON {
        private final JSONObject json = new JSONObject();
        private int numResultFilesAdded;

        ResultsJSON(boolean writeOnJVMExit) {
            try {
                json.put("results", new JSONObject());
                JSONObject build = new JSONObject();
                json.put("build", build);

                addBuildInfo(build, "jenkins_build_number", "BUILD_NUMBER");
                addBuildInfo(build, "jenkins_build_id", "BUILD_ID");
                addBuildInfo(build, "git_branch", "GIT_BRANCH", "CURRENT_GIT_BRANCH");
                addBuildInfo(build, "git_commit", "GIT_COMMIT", "CURRENT_GIT_COMMIT");
                addBuildInfo(build, "aura_version", "AURA_VERSION");
                addBuildInfo(build, "author_email", "AUTHOR_EMAIL");
                addBuildInfo(build, "changelists", "CHANGELISTS");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "error adding build info", e);
            }

            if (writeOnJVMExit) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        if (numResultFilesAdded > 0) {
                            File file = new File(RESULTS_DIR + "/Results.json");
                            writeFile(file, json.toString(), "Results.json");
                        }
                    }
                });
            }
        }

        void addResultsFile(File file) {
            numResultFilesAdded++;
            // i.e. timelines: { ui: { list: [..., ..., ...] }}
            try {
                JSONArray list = getListParent(file).getJSONArray("list");
                // put filenames sorted in the JSONArray
                String fileName = file.getName();
                int insertIndex = list.length();
                for (int i = 0; i < list.length(); i++) {
                    if (fileName.compareTo(list.getString(i)) < 0) {
                        insertIndex = i;
                        break;
                    }
                }
                for (int i = list.length() - 1; i >= insertIndex; i--) {
                    list.put(i + 1, list.get(i));
                }
                list.put(insertIndex, fileName);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "error adding results file: " + file, e);
            }
        }

        public void removeResultsFile(File file) {
            try {
                JSONObject parent = getListParent(file);
                JSONArray list = parent.getJSONArray("list");
                JSONArray trimmedList = new JSONArray();
                String fileName = file.getName();
                for (int i = 0; i < list.length(); i++) {
                    String value = list.getString(i);
                    if (!fileName.equals(value)) {
                        trimmedList.put(value);
                    }
                }
                parent.put("list", trimmedList);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "error removing results file: " + file, e);
            }
        }

        JSONObject getJSON() {
            return json;
        }

        private JSONObject getListParent(File file) throws JSONException {
            String relativePath = file.getParentFile().getPath();
            relativePath = relativePath.substring(RESULTS_DIR.getPath().length() + 1);
            String[] folders = relativePath.split("/");
            JSONObject parent = json.getJSONObject("results");
            for (String folder : folders) {
                if (!parent.has(folder)) {
                    parent.put(folder, new JSONObject());
                }
                parent = parent.getJSONObject(folder);
            }
            if (!parent.has("list")) {
                parent.put("list", new JSONArray());
            }
            return parent;
        }

        private static void addBuildInfo(JSONObject build, String key, String... envvars) throws JSONException {
            for (String envvar : envvars) {
                String value = System.getenv(envvar);
                if (value != null && value.trim().length() > 0) {
                    build.put(key, value);
                    return; // uses first non-null
                }
            }
        }
    }

    // write a _all.json for each namespace

    private static final AllGoldfilesJSON ALL_GOLDFILES_JSON = new AllGoldfilesJSON(true);

    /**
     * Writes a single _all.json containing all the goldfiles in a namespace
     */
    private static final class AllGoldfilesJSON {
        private final Map<String, JSONObject> namespaceToAllJson = Maps.newHashMap();

        AllGoldfilesJSON(boolean writeOnJVMExit) {
            if (writeOnJVMExit) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        if (namespaceToAllJson.size() > 0) {
                            // write the goldfile inside each namespace
                            for (String namespace : namespaceToAllJson.keySet()) {
                                File file = new File(RESULTS_DIR + "/goldfiles/" + namespace + "/_all.json");
                                writeFile(file, namespaceToAllJson.get(namespace).toString(), namespace + "/_all.json");
                            }
                        }
                    }
                });
            }
        }

        void addGoldfile(String fileName, PerfMetrics metrics) throws JSONException {
            int index = fileName.lastIndexOf('/');
            String namespace = (index != -1) ? fileName.substring(0, index) : "";
            String componentName = fileName.substring(index + 1);
            if (!namespaceToAllJson.containsKey(namespace)) {
                namespaceToAllJson.put(namespace, new JSONObject());
            }
            JSONObject allJson = namespaceToAllJson.get(namespace);
            allJson.put(componentName, metrics.toJSONArrayWithoutDetails());
        }
    }
}
