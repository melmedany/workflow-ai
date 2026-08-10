package io.workflowai.domain.task;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SchedulingIntentDetector {

    private static final Pattern SCHEDULE_COMMAND = Pattern.compile("^\\s*/schedule\\b\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern RESEMBLES_SCHEDULING = Pattern.compile(
            "/schedule\\b"
                    + "|\\bevery\\s+(day|night|morning|evening|hour|minute|week|month|year|weekday|weekend)\\b"
                    + "|\\brecurring\\b"
                    + "|\\bcron\\b"
                    + "|\\bschedule\\s+(a|this|that|an)\\b"
                    + "|\\bset\\s+up\\s+a\\s+(recurring|scheduled)\\b",
            Pattern.CASE_INSENSITIVE);

    private SchedulingIntentDetector() {
    }

    public static Optional<String> extractCommand(String rawMessage) {
        if (rawMessage == null) {
            return Optional.empty();
        }
        Matcher matcher = SCHEDULE_COMMAND.matcher(rawMessage);
        if (!matcher.find() || matcher.start() != 0) {
            return Optional.empty();
        }
        return Optional.of(rawMessage.substring(matcher.end()).trim());
    }

    public static boolean resemblesSchedulingRequest(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return RESEMBLES_SCHEDULING.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}