package org.jenkinsci.plugins.chatwork;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import jenkins.model.JenkinsLocationConfiguration;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

public class ChatWorkNotifier extends Notifier {

    final private String apiToken;
    final private String roomId;
    final private String message;

    private static final Logger LOG = Logger.getLogger(ChatWorkNotifier.class);

    @DataBoundConstructor
    public ChatWorkNotifier(String apiToken, String roomId, String message) {
        this.apiToken = apiToken;
        this.roomId = roomId;
        this.message = message;
    }

    /**
     * @return the apiToken
     */
    public String getApiToken() {
        return apiToken;
    }

    /**
     * @return the roomId
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        final String apiUrl = String.format("https://api.chatwork.com/v1/rooms/%s/messages", roomId);
        final String message = generatedMessage(build);
        final String postData = "body=" + URLEncoder.encode(message, "UTF-8"); 

        URL url = new URL(apiUrl);
        URLConnection connection = null;
        OutputStream os = null;
        OutputStreamWriter osw = null;
        InputStream is = null;
        InputStreamReader isr = null;
        BufferedReader reader = null;
        
        try {
            connection = url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestProperty("X-ChatWorkToken", apiToken);

            os = connection.getOutputStream();
            osw = new OutputStreamWriter(os, "UTF-8");
            osw.write(postData);
            IOUtils.closeQuietly(osw);
            osw = null;
            IOUtils.closeQuietly(os);
            os = null;

            is = connection.getInputStream();
            isr = new InputStreamReader(is);
            reader = new BufferedReader(isr);
            String s;
            while ((s = reader.readLine()) != null) {
                // do nothing
            }
        } catch (IOException e) {
            // ignore because notification failures make build failures!
            LOG.error("[ChatWork] Failed to notification", e);
        } finally {
            IOUtils.closeQuietly(osw);
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(reader);
            IOUtils.closeQuietly(isr);
            IOUtils.closeQuietly(is);
            connection = null;
        }
        return true;    }
    
    private String generatedMessage(AbstractBuild<?, ?> build) {
        
        final StringBuilder userBuilder = new StringBuilder();
        for(User user: build.getCulprits()) {
            userBuilder.append(user.getFullName() + " ");
        }
        
        final StringBuilder changeSetBuilder = new StringBuilder();
        for(ChangeLogSet.Entry entry: build.getChangeSet()) {
            changeSetBuilder.append(entry.getAuthor() + " : " + entry.getMsg() + "\n");
        }
        
        String replacedMessage = message.replace("${user}", userBuilder.toString());
        replacedMessage = replacedMessage.replace("${result}", build.getResult().toString());
        replacedMessage = replacedMessage.replace("${project}", build.getProject().getName());
        replacedMessage = replacedMessage.replace("${number}", String.valueOf(build.number));
        replacedMessage = replacedMessage.replace("${url}", JenkinsLocationConfiguration.get().getUrl() + build.getUrl());
        replacedMessage = replacedMessage.replace("${changeSet}", changeSetBuilder.toString());

        return replacedMessage;
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }
        
        @Override
        public String getHelpFile() {
            return "/plugin/chatwork-plugin/ChatWorkNotifier.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> project) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ChatWork";
        }
    }
}
