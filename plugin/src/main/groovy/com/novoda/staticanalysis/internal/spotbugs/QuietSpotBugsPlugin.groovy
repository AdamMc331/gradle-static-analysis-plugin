package com.novoda.staticanalysis.internal.spotbugs

import com.github.spotbugs.SpotBugsPlugin
import com.github.spotbugs.SpotBugsTask
import com.novoda.staticanalysis.internal.QuietLogger
import org.gradle.api.logging.Logger

public class QuietSpotBugsPlugin extends SpotBugsPlugin {

    @Override
    protected Class<SpotBugsTask> getTaskType() {
        return Task
    }

    static class Task extends SpotBugsTask {

        public Task() {
            super()
        }

        @Override
        public Logger getLogger() {
            QuietLogger.INSTANCE
        }
    }

}
