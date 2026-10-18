package tech.voice.icety.application.service;

import java.time.LocalTime;
import java.util.Locale;
import java.util.regex.Pattern;

final class ExplicitTimeExtractor {

    private static final Pattern TIME = Pattern.compile(
            "(?iuU)\\b(?:às|as)\\s+([01]?\\d|2[0-3])"
                    + "(?::([0-5]\\d)|\\s*h(?:oras?)?(?:([0-5]\\d))?)"
                    + "(?:\\s+da\\s+(manhã|manha|tarde|noite|madrugada))?"
                    + "(?![\\p{L}\\d:])");
    private static final Pattern AMBIGUOUS = Pattern.compile(
            "(?iuU)\\b(não|nao|nem|exceto|entre|ou|até|ate|depois|antes|após|apos)\\b");

    private ExplicitTimeExtractor() { }

    static String extract(String text) {
        if (AMBIGUOUS.matcher(text).find()) {
            return null;
        }
        var match = TIME.matcher(text);
        if (!match.find()) {
            return null;
        }
        int hour = Integer.parseInt(match.group(1));
        String minutes = match.group(2) != null ? match.group(2) : match.group(3);
        int minute = minutes == null ? 0 : Integer.parseInt(minutes);
        String qualifier = match.group(4);
        if (qualifier != null) {
            qualifier = qualifier.toLowerCase(Locale.ROOT);
            if ((qualifier.equals("tarde") || qualifier.equals("noite")) && hour < 12) {
                hour += 12;
            } else if ((qualifier.equals("manhã") || qualifier.equals("manha")
                    || qualifier.equals("madrugada") || qualifier.equals("noite")) && hour == 12) {
                hour = 0;
            }
        }
        if (match.find()) {
            return null;
        }
        return LocalTime.of(hour, minute).toString();
    }
}
