package io.workflowai.domain.task;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SchedulingIntentDetector {

    private static final Pattern SCHEDULE_COMMAND = Pattern.compile("^\\s*/schedule\\b\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern RESEMBLES_SCHEDULING = Pattern.compile(
            "every\\s+(day|night|morning|evening|hour|minute|week|month|year|weekday|weekend)\\b"
                    + "|\\brecurring\\b"
                    + "|\\bcron\\b"
                    + "|\\bschedule\\s+(a|this|that|an)\\b"
                    + "|\\bset\\s+up\\s+a\\s+(recurring|scheduled)\\b"
                    + "|\\b(after|in)\\s+\\d+\\s+(minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)\\b"
                    + "|\\bat\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\b"
                    + "|\\bon\\s+(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|today|tonight)\\b"
                    + "|\\bremind\\s+me\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TEMPORAL_PATTERN = Pattern.compile(
            "\\s+(?:after|in)\\s+\\d+\\s+(?:minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)\\b"
                    + "|\\s+at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?\\b"
                    + "|\\s+on\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|today|tonight)\\b"
                    + "|\\s+every\\s+(?:day|night|morning|evening|hour|minute|week|month|year|weekday|weekend)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMAND_PREFIX = Pattern.compile(
            "^\\s*(?:tell\\s+me|remind\\s+me(?:\\s+(?:to|about))?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private SchedulingIntentDetector() {
    }

    public static Optional<String> extractCommand(String rawMessage) {
        if (rawMessage == null) {
            return Optional.empty();
        }
        Matcher matcher = SCHEDULE_COMMAND.matcher(rawMessage);
        if (!matcher.lookingAt()) {
            return Optional.empty();
        }
        return Optional.of(rawMessage.substring(matcher.end()).trim());
    }

    public static String cleanInstruction(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        if (!RESEMBLES_SCHEDULING.matcher(text).find()) {
            return text;
        }

        String cleaned = TEMPORAL_PATTERN.matcher(text).replaceAll(" ");

        Matcher prefixMatcher = COMMAND_PREFIX.matcher(cleaned);
        if (prefixMatcher.lookingAt()) {
            String afterPrefix = cleaned.substring(prefixMatcher.end());
            cleaned = afterPrefix.isBlank() ? "" : capitalizeFirst(afterPrefix);
        }

        cleaned = MULTI_SPACE.matcher(cleaned.trim()).replaceAll(" ");

        return cleaned;
    }

    private static String capitalizeFirst(String s) {
        if (s.isEmpty()) {
            return s;
        }
        int firstCodePoint = s.codePointAt(0);
        int charCount = Character.charCount(firstCodePoint);
        return new StringBuilder()
                .appendCodePoint(Character.toUpperCase(firstCodePoint))
                .append(s.substring(charCount))
                .toString();
    }
}