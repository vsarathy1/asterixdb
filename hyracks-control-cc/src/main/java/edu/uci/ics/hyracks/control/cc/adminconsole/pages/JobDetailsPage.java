/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.cc.adminconsole.pages;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.EmptyPanel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.string.StringValue;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.uci.ics.hyracks.api.dataflow.ActivityId;
import edu.uci.ics.hyracks.api.dataflow.TaskId;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.cc.work.GetJobActivityGraphJSONWork;
import edu.uci.ics.hyracks.control.cc.work.GetJobRunJSONWork;
import edu.uci.ics.hyracks.control.cc.work.GetJobSpecificationJSONWork;

public class JobDetailsPage extends AbstractPage {
    private static final long serialVersionUID = 1L;

    private static final int HEIGHT = 29;
    private static final int WIDTH = 9;

    public JobDetailsPage(PageParameters params) throws Exception {
        ClusterControllerService ccs = getAdminConsoleApplication().getClusterControllerService();

        StringValue jobIdStr = params.get("job-id");

        JobId jobId = JobId.parse(jobIdStr.toString());

        GetJobSpecificationJSONWork gjsw = new GetJobSpecificationJSONWork(ccs, jobId);
        ccs.getWorkQueue().scheduleAndSync(gjsw);
        Label jobspec = new Label("job-specification", gjsw.getJSON().toString());
        jobspec.setEscapeModelStrings(false);
        add(jobspec);

        GetJobActivityGraphJSONWork gjagw = new GetJobActivityGraphJSONWork(ccs, jobId);
        ccs.getWorkQueue().scheduleAndSync(gjagw);
        Label jag = new Label("job-activity-graph", gjagw.getJSON().toString());
        jag.setEscapeModelStrings(false);
        add(jag);

        JSONObject jagO = gjagw.getJSON();

        Map<ActivityId, String> activityMap = new HashMap<ActivityId, String>();
        if (jagO.has("activities")) {
            JSONArray aArray = jagO.getJSONArray("activities");
            for (int i = 0; i < aArray.length(); ++i) {
                JSONObject aO = aArray.getJSONObject(i);
                ActivityId aid = ActivityId.parse(aO.getString("id"));
                String className = aO.getString("java-class");

                activityMap.put(aid, className);
            }
        }

        GetJobRunJSONWork gjrw = new GetJobRunJSONWork(ccs, jobId);
        ccs.getWorkQueue().scheduleAndSync(gjrw);
        Label jobrun = new Label("job-run", gjrw.getJSON().toString());
        jobrun.setEscapeModelStrings(false);
        add(jobrun);

        JSONObject jrO = gjrw.getJSON();

        List<TaskClusterAttempt[]> tcList = new ArrayList<TaskClusterAttempt[]>();
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        if (jrO.has("activity-clusters")) {
            JSONArray acA = jrO.getJSONArray("activity-clusters");
            for (int i = 0; i < acA.length(); ++i) {
                JSONObject acO = acA.getJSONObject(i);
                if (acO.has("plan")) {
                    JSONObject planO = acO.getJSONObject("plan");
                    if (planO.has("task-clusters")) {
                        JSONArray tcA = planO.getJSONArray("task-clusters");
                        for (int j = 0; j < tcA.length(); ++j) {
                            JSONObject tcO = tcA.getJSONObject(j);
                            String tcId = tcO.getString("task-cluster-id");
                            if (tcO.has("attempts")) {
                                JSONArray tcaA = tcO.getJSONArray("attempts");
                                TaskClusterAttempt[] tcAttempts = new TaskClusterAttempt[tcaA.length()];
                                for (int k = 0; k < tcaA.length(); ++k) {
                                    JSONObject tcaO = tcaA.getJSONObject(k);
                                    int attempt = tcaO.getInt("attempt");
                                    long startTime = tcaO.getLong("start-time");
                                    long endTime = tcaO.getLong("end-time");

                                    tcAttempts[k] = new TaskClusterAttempt(tcId, attempt, startTime, endTime);
                                    if (startTime < minTime) {
                                        minTime = startTime;
                                    }
                                    if (endTime > maxTime) {
                                        maxTime = endTime;
                                    }
                                    if (tcaO.has("task-attempts")) {
                                        JSONArray taArray = tcaO.getJSONArray("task-attempts");
                                        tcAttempts[k].tasks = new TaskAttempt[taArray.length()];
                                        for (int l = 0; l < taArray.length(); ++l) {
                                            JSONObject taO = taArray.getJSONObject(l);
                                            TaskAttempt ta = new TaskAttempt(taO.getString("task-id"),
                                                    taO.getLong("start-time"), taO.getLong("end-time"));
                                            tcAttempts[k].tasks[l] = ta;
                                            TaskId tid = TaskId.parse(taO.getString("task-id"));
                                            ta.name = activityMap.get(tid.getActivityId());
                                            ta.partition = tid.getPartition();
                                        }
                                        Arrays.sort(tcAttempts[k].tasks, new Comparator<TaskAttempt>() {
                                            @Override
                                            public int compare(TaskAttempt o1, TaskAttempt o2) {
                                                return o1.startTime < o2.startTime ? -1
                                                        : (o1.startTime > o2.startTime ? 1 : 0);
                                            }
                                        });
                                    }
                                }
                                Arrays.sort(tcAttempts, new Comparator<TaskClusterAttempt>() {
                                    @Override
                                    public int compare(TaskClusterAttempt o1, TaskClusterAttempt o2) {
                                        return o1.startTime < o2.startTime ? -1 : (o1.startTime > o2.startTime ? 1 : 0);
                                    }
                                });
                                tcList.add(tcAttempts);
                            }
                        }
                    }
                }
            }
        }

        if (!tcList.isEmpty()) {
            Collections.sort(tcList, new Comparator<TaskClusterAttempt[]>() {
                @Override
                public int compare(TaskClusterAttempt[] o1, TaskClusterAttempt[] o2) {
                    if (o1.length == 0) {
                        return o2.length == 0 ? 0 : -1;
                    } else if (o2.length == 0) {
                        return 1;
                    }
                    return o1[0].startTime < o2[0].startTime ? -1 : (o1[0].startTime > o2[0].startTime ? 1 : 0);
                }
            });
            long range = maxTime - minTime;

            double leftOffset = 20;

            int xWidth = 1024;
            double width = ((double) xWidth) / range;
            StringBuilder buffer = new StringBuilder();
            int y = 0;
            for (TaskClusterAttempt[] tcAttempts : tcList) {
                for (int i = 0; i < tcAttempts.length; ++i) {
                    TaskClusterAttempt tca = tcAttempts[i];
                    long startTime = tca.startTime - minTime;
                    long endTime = tca.endTime - minTime;
                    buffer.append("<rect x=\"").append(startTime * width + leftOffset).append("\" y=\"")
                            .append(y * (HEIGHT + 1)).append("\" width=\"").append(width * (endTime - startTime))
                            .append("\" height=\"").append(HEIGHT).append("\"/>\n");
                    buffer.append("<text x=\"").append(endTime * width + leftOffset + 20).append("\" y=\"")
                            .append(y * (HEIGHT + 1) + HEIGHT * 3 / 4).append("\">")
                            .append((endTime - startTime) + " ms").append("</text>\n");
                    ++y;
                    for (int j = 0; j < tca.tasks.length; ++j) {
                        TaskAttempt ta = tca.tasks[j];
                        long tStartTime = ta.startTime - minTime;
                        long tEndTime = ta.endTime - minTime;
                        buffer.append("<rect x=\"").append(tStartTime * width + leftOffset).append("\" y=\"")
                                .append(y * (HEIGHT + 1) + HEIGHT / 4).append("\" width=\"")
                                .append(width * (tEndTime - tStartTime)).append("\" height=\"").append(HEIGHT / 2)
                                .append("\"/>\n");
                        buffer.append("<text x=\"").append(tEndTime * width + leftOffset + 20).append("\" y=\"")
                                .append(y * (HEIGHT + 1) + HEIGHT * 3 / 4).append("\">")
                                .append((tEndTime - tStartTime) + " ms (" + ta.name + ":" + ta.partition + ")")
                                .append("</text>\n");
                        ++y;
                    }
                }
            }
            buffer.append("<rect x=\"").append(leftOffset).append("\" y=\"").append(0).append("\" width=\"").append(1)
                    .append("\" height=\"").append((y + 2) * (HEIGHT + 1)).append("\"/>\n");
            buffer.append("<rect x=\"").append(0).append("\" y=\"").append((y + 1) * (HEIGHT + 1))
                    .append("\" width=\"").append(xWidth + 2 * leftOffset).append("\" height=\"").append(1)
                    .append("\"/>\n");
            buffer.append("</svg>");
            Label markup = new Label("job-timeline",
                    "<svg version=\"1.1\"\nxmlns=\"http://www.w3.org/2000/svg\" width=\"" + (xWidth * 1.5)
                            + "\" height=\"" + ((y + 2) * (HEIGHT + 1)) + "\">\n" + buffer.toString());
            markup.setEscapeModelStrings(false);
            add(markup);
        }

        List<TaskProfile> taskProfiles = new ArrayList<TaskProfile>();
        if (jrO.has("profile")) {
            JSONObject pO = jrO.getJSONObject("profile");
            if (pO.has("joblets")) {
                JSONArray jobletsA = pO.getJSONArray("joblets");
                for (int i = 0; i < jobletsA.length(); ++i) {
                    JSONObject jobletO = jobletsA.getJSONObject(i);
                    if (jobletO.has("tasks")) {
                        JSONArray tasksA = jobletO.getJSONArray("tasks");
                        for (int j = 0; j < tasksA.length(); ++j) {
                            JSONObject taskO = tasksA.getJSONObject(j);
                            String activityId = taskO.getString("activity-id");
                            int partition = taskO.getInt("partition");
                            int attempt = taskO.getInt("attempt");
                            if (taskO.has("partition-send-profile")) {
                                JSONArray taskProfilesA = taskO.getJSONArray("partition-send-profile");
                                for (int k = 0; k < taskProfilesA.length(); ++k) {
                                    JSONObject ppO = taskProfilesA.getJSONObject(k);
                                    long openTime = ppO.getLong("open-time");
                                    long closeTime = ppO.getLong("close-time");
                                    JSONArray frameTimesA = ppO.getJSONArray("frame-times");
                                    long[] frameTimes = new long[frameTimesA.length()];
                                    for (int l = 0; l < frameTimes.length; ++l) {
                                        frameTimes[l] = frameTimesA.getLong(l);
                                    }
                                    taskProfiles.add(new TaskProfile(activityId, partition, attempt, openTime,
                                            closeTime, frameTimes));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!taskProfiles.isEmpty()) {
            Collections.sort(taskProfiles, new Comparator<TaskProfile>() {
                @Override
                public int compare(TaskProfile o1, TaskProfile o2) {
                    return o1.openTime < o2.openTime ? -1 : (o1.openTime > o2.openTime ? 1 : 0);
                }
            });
            long startTime = taskProfiles.get(0).openTime;
            long timeRange = taskProfiles.get(taskProfiles.size() - 1).closeTime - startTime;
            int n = taskProfiles.size();
            StringBuilder buffer = new StringBuilder();
            buffer.append("<svg viewBox=\"0 0 ").append((timeRange + 1) * (WIDTH + 1)).append(' ')
                    .append((n + 1) * (HEIGHT + 1)).append("\" version=\"1.1\"\n");
            buffer.append("xmlns=\"http://www.w3.org/2000/svg\">\n");
            for (int i = 0; i < n; ++i) {
                TaskProfile tp = taskProfiles.get(i);
                open(buffer, i, tp.openTime - startTime);
                for (long ft : tp.frameTimes) {
                    nextFrame(buffer, i, ft - startTime);
                }
                close(buffer, i, tp.closeTime - startTime);
            }
            buffer.append("</svg>");
            Label markup = new Label("job-timeline-profile", buffer.toString());
            markup.setEscapeModelStrings(false);
            add(markup);
        } else {
            add(new EmptyPanel("job-timeline-profile"));
        }
    }

    private void open(StringBuilder buffer, int i, long openTime) {
        buffer.append("<rect x=\"").append(openTime * (WIDTH + 1)).append("\" y=\"").append(i * (HEIGHT + 1))
                .append("\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT).append("\"/>\n");
    }

    private void close(StringBuilder buffer, int i, long closeTime) {
        buffer.append("<rect x=\"").append(closeTime * (WIDTH + 1)).append("\" y=\"").append(i * (HEIGHT + 1))
                .append("\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT).append("\"/>\n");
    }

    private void nextFrame(StringBuilder buffer, int i, long frameTime) {
        buffer.append("<rect x=\"").append(frameTime * (WIDTH + 1)).append("\" y=\"").append(i * (HEIGHT + 1))
                .append("\" width=\"").append(WIDTH).append("\" height=\"").append(HEIGHT).append("\"/>\n");
    }

    private static class TaskAttempt {
        private String taskId;
        private long startTime;
        private long endTime;
        private String name;
        private int partition;

        public TaskAttempt(String taskId, long startTime, long endTime) {
            this.taskId = taskId;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static class TaskClusterAttempt {
        private String tcId;
        private int attempt;
        private long startTime;
        private long endTime;
        private TaskAttempt[] tasks;

        public TaskClusterAttempt(String tcId, int attempt, long startTime, long endTime) {
            this.tcId = tcId;
            this.attempt = attempt;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

    private static class TaskProfile {
        private String activityId;
        private int partition;
        private int attempt;
        private long openTime;
        private long closeTime;
        private long[] frameTimes;

        public TaskProfile(String activityId, int partition, int attempt, long openTime, long closeTime,
                long[] frameTimes) {
            this.activityId = activityId;
            this.partition = partition;
            this.activityId = activityId;
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.frameTimes = frameTimes;
        }
    }
}