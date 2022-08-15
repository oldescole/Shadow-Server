package su.sres.shadowserver.workers;

import io.dropwizard.servlets.tasks.Task;
import su.sres.shadowserver.util.logging.RequestLogManager;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class DisableRequestLoggingTask extends Task {

    public DisableRequestLoggingTask() {
        super("disable-request-logging");
    }

    @Override
    public void execute(final Map<String, List<String>> map, final PrintWriter printWriter) {
        RequestLogManager.setRequestLoggingEnabled(false);
    }
}
