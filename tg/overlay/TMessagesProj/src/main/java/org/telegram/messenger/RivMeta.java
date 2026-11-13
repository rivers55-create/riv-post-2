package org.telegram.messenger;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Riv Post: cleans up messy video file names/captions into tidy titles, and (optionally, with
 * the user's own API keys) looks up posters/backdrops/overviews/cast from TMDb or TheTVDB.
 * Nothing here changes anything in Telegram; it only affects how Riv Videos displays things.
 *
 * This is a faithful Java port of the old Riv Post app's Meta.kt.
 */
public class RivMeta {

    // ---------------- title cleanup ----------------

    private static final Pattern JUNK_WORDS = Pattern.compile(
            "(?i)\\b(2160p|1440p|1080p|720p|576p|480p|360p|4k|uhd|fhd|hd|sd|x264|x265|h\\.?264|h\\.?265|hevc|avc|10bit|8bit|hdr10?\\+?|dv|" +
                    "web-?dl|web-?rip|webrip|bluray|blu-ray|brrip|bdrip|dvdrip|hdrip|hdtv|hdcam|cam|ts|remux|proper|repack|extended|unrated|" +
                    "aac\\d?(\\.\\d)?|ac3|eac3|ddp?\\d?(\\.\\d)?|dts(-hd)?|atmos|truehd|mp4|mkv|avi|nf|amzn|dsnp|hmax|atvp|yts|rarbg|psa|pahe|" +
                    "subbed|dubbed|multi|dual|eng|arabic|arab|mkvcage|esub|hc)\\b");
    private static final Pattern AR_JUNK = Pattern.compile(
            "(مترجم(ة)?|مدبلج(ة)?|حصري(ا|اً)?|جودة\\s*عالية|بجودة\\s*\\S+|اونلاين|للتحميل|تحميل|مشاهدة|كامل(ة)?|نسخة\\s*\\S+|HD)");
    private static final Pattern LINKS = Pattern.compile("(https?://\\S+|t\\.me/\\S+|www\\.\\S+|@\\w+|#\\S+)");
    private static final Pattern EMOJI = Pattern.compile("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]");
    private static final Pattern SXE = Pattern.compile("(?i)\\bS(\\d{1,2})\\s*[ .-]?E(\\d{1,3})\\b");
    private static final Pattern X_FORMAT = Pattern.compile("\\b(\\d{1,2})x(\\d{2,3})\\b");
    private static final Pattern SEASON_WORD = Pattern.compile("(?i)(season|الموسم)\\s*(\\d{1,2})");
    private static final Pattern EPISODE_WORD = Pattern.compile("(?i)(episode|ep|الحلقة|حلقة)\\s*(\\d{1,3})");
    private static final Pattern YEAR = Pattern.compile("\\b(19[3-9]\\d|20[0-4]\\d)\\b");
    private static final Pattern BRACKETS = Pattern.compile("[\\[(\\{【].*?[\\])\\}】]");
    private static final Pattern DOTS_UNDERSCORES = Pattern.compile("[._]+");
    private static final Pattern SEPARATORS = Pattern.compile("[|•»«~*=+_]+");
    private static final Pattern TRAILING_DASH = Pattern.compile("\\s+-\\s*$");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");
    private static final Pattern JOIN_WORDS = Pattern.compile("(?i)(join|subscribe|follow|channel|قناتنا|انضم|اشترك|تابعونا|للمزيد)");

    /** Tidy title pieces pulled out of a messy file name or caption. */
    public static class CleanTitle {
        public final String title;
        public final int year;
        public final int season;
        public final int episode;

        public CleanTitle(String title, int year, int season, int episode) {
            this.title = title;
            this.year = year;
            this.season = season;
            this.episode = episode;
        }

        public String label() {
            StringBuilder sb = new StringBuilder(title);
            if (season > 0 && episode > 0) {
                sb.append(String.format(Locale.US, " · S%02dE%02d", season, episode));
            } else if (episode > 0) {
                sb.append(" · E").append(episode);
            }
            if (year > 0 && season == 0) {
                sb.append(" (").append(year).append(")");
            }
            return sb.toString();
        }
    }

    public static class CastMember {
        public final String name;
        public final String role;
        public final String photo;

        public CastMember(String name, String role, String photo) {
            this.name = name;
            this.role = role;
            this.photo = photo;
        }
    }

    /** Info from TMDb or TheTVDB (optional, with the user's own keys). */
    public static class OnlineInfo {
        public String title = "";
        public String year = "";
        public String overview = "";
        public double rating = 0;
        public String poster = "";
        public String backdrop = "";
        public List<String> genres = new ArrayList<>();
        public int runtime = 0;
        public List<CastMember> cast = new ArrayList<>();
        public int season = 0;
        public int episode = 0;
        public String epTitle = "";
        public String epOverview = "";
        public String epStill = "";
        public String seasonPoster = "";
        public String source = "";
    }

    /** What videos/songs give RivMeta to clean up: a file name plus an optional caption. */
    public interface Source {
        String fileName();

        String caption();

        String rawTitle();
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static CleanTitle clean(String raw) {
        String s = raw;
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot >= s.length() - 6) {
            s = s.substring(0, dot);
        }
        s = LINKS.matcher(s).replaceAll(" ");
        s = EMOJI.matcher(s).replaceAll(" ");

        int season = 0, episode = 0;
        Matcher m = SXE.matcher(s);
        if (m.find()) {
            season = toInt(m.group(1));
            episode = toInt(m.group(2));
        }
        if (season == 0) {
            m = X_FORMAT.matcher(s);
            if (m.find()) {
                season = toInt(m.group(1));
                episode = toInt(m.group(2));
            }
        }
        if (season == 0) {
            m = SEASON_WORD.matcher(s);
            if (m.find()) season = toInt(m.group(2));
        }
        if (episode == 0) {
            m = EPISODE_WORD.matcher(s);
            if (m.find()) episode = toInt(m.group(2));
        }

        int year = 0;
        Matcher ym = YEAR.matcher(s);
        while (ym.find()) {
            year = toInt(ym.group(1));
        }

        Integer cutAt = null;
        Matcher sxeM = SXE.matcher(s);
        if (sxeM.find()) cutAt = sxeM.start();
        Matcher xfM = X_FORMAT.matcher(s);
        if (xfM.find()) {
            if (cutAt == null || xfM.start() < cutAt) cutAt = xfM.start();
        }
        Matcher yM = YEAR.matcher(s);
        if (yM.find() && yM.start() > 3) {
            if (cutAt == null || yM.start() < cutAt) cutAt = yM.start();
        }
        if (cutAt != null && cutAt > 2) {
            s = s.substring(0, cutAt);
        }

        s = BRACKETS.matcher(s).replaceAll(" ");
        s = DOTS_UNDERSCORES.matcher(s).replaceAll(" ");
        s = JUNK_WORDS.matcher(s).replaceAll(" ");
        s = AR_JUNK.matcher(s).replaceAll(" ");
        s = SEASON_WORD.matcher(s).replaceAll(" ");
        s = EPISODE_WORD.matcher(s).replaceAll(" ");
        s = SEPARATORS.matcher(s).replaceAll(" ");
        s = TRAILING_DASH.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ");
        s = s.trim();
        s = trimChars(s, " -:,.".toCharArray());

        if (!s.isEmpty() && s.equals(s.toLowerCase(Locale.US)) && containsLetter(s)) {
            StringBuilder cased = new StringBuilder();
            for (String w : s.split(" ")) {
                if (w.isEmpty()) continue;
                if (cased.length() > 0) cased.append(' ');
                cased.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            s = cased.toString();
        }
        return new CleanTitle(s, year, season, episode);
    }

    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') return true;
        }
        return false;
    }

    private static String trimChars(String s, char[] chars) {
        int start = 0, end = s.length();
        while (start < end && indexOf(chars, s.charAt(start)) >= 0) start++;
        while (end > start && indexOf(chars, s.charAt(end - 1)) >= 0) end--;
        return s.substring(start, end);
    }

    private static int indexOf(char[] arr, char c) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == c) return i;
        return -1;
    }

    /** Best title for a video: tidy caption first line if it looks like a name, else the file name. */
    public static CleanTitle titleOf(Source v) {
        String fileName = v.fileName();
        CleanTitle fromFile = (fileName != null && !fileName.isEmpty()) ? clean(fileName) : null;

        String firstLine = "";
        String caption = v.caption();
        if (caption != null) {
            for (String line : caption.split("\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !LINKS.matcher(t).replaceAll("").trim().isEmpty()) {
                    firstLine = t;
                    break;
                }
            }
        }
        CleanTitle fromCap = !firstLine.isEmpty() ? clean(firstLine) : null;

        List<CleanTitle> good = new ArrayList<>();
        for (CleanTitle c : new CleanTitle[]{fromCap, fromFile}) {
            if (c == null) continue;
            int letters = 0;
            for (int i = 0; i < c.title.length(); i++) if (Character.isLetter(c.title.charAt(i))) letters++;
            if (letters >= 2 && c.title.length() <= 70) good.add(c);
        }
        CleanTitle best = !good.isEmpty() ? good.get(0) : (fromFile != null ? fromFile : (fromCap != null ? fromCap : new CleanTitle(v.rawTitle(), 0, 0, 0)));
        CleanTitle other = good.size() > 1 ? good.get(1) : null;

        int year = best.year > 0 ? best.year : (other != null ? other.year : 0);
        int season = best.season > 0 ? best.season : (other != null ? other.season : 0);
        int episode = best.episode > 0 ? best.episode : (other != null ? other.episode : 0);
        return new CleanTitle(best.title, year, season, episode);
    }

    public static String title(Source v) {
        if (!RivPrefs.b(RivPrefs.CLEAN_TITLES, true)) return v.rawTitle();
        CleanTitle t = titleOf(v);
        String label = t.label();
        return label.isEmpty() ? v.rawTitle() : label;
    }

    /** Caption without links, mentions, hashtags, "join us" lines and emoji spam. */
    public static String description(Source v) {
        if (!RivPrefs.b(RivPrefs.CLEAN_TITLES, true)) return v.caption() == null ? "" : v.caption();
        String caption = v.caption();
        if (caption == null || caption.isEmpty()) return "";
        String[] lines = caption.split("\n");
        List<String> kept = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String l = LINKS.matcher(lines[i]).replaceAll("");
            l = EMOJI.matcher(l).replaceAll("");
            l = l.trim();
            int letters = 0;
            for (int j = 0; j < l.length(); j++) if (Character.isLetter(l.charAt(j))) letters++;
            if (letters < 3) continue;
            if (JOIN_WORDS.matcher(l).find()) continue;
            if (!kept.contains(l)) kept.add(l);
        }
        return String.join("\n", kept).trim();
    }

    // ---------------- online (TMDb / TheTVDB, optional) ----------------

    private static final HashMap<String, OnlineInfo> memCache = new HashMap<>();
    private static final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);

    public static boolean hasOnline() {
        return (RivPrefs.b(RivPrefs.TMDB_ON) && !RivPrefs.s(RivPrefs.TMDB_KEY, "").isEmpty())
                || (RivPrefs.b(RivPrefs.TVDB_ON, false) && !RivPrefs.s(RivPrefs.TVDB_KEY, "").isEmpty());
    }

    public interface Callback {
        void onResult(OnlineInfo info);
    }

    /** Looks up metadata on a background thread, then calls back on the UI thread. Caches to disk and memory. */
    public static void lookup(Context ctx, Source v, boolean arabic, Callback cb) {
        if (!hasOnline()) {
            cb.onResult(null);
            return;
        }
        CleanTitle t = titleOf(v);
        if (t.title.length() < 2) {
            cb.onResult(null);
            return;
        }
        String showSource = RivPrefs.s(RivPrefs.SHOW_SOURCE, "tmdb");
        String cacheKey = t.title.toLowerCase(Locale.US) + "|" + t.year + "|" + t.season + "|" + t.episode + "|" + showSource + "|" + arabic;
        OnlineInfo cached = memCache.get(cacheKey);
        if (cached != null || memCache.containsKey(cacheKey)) {
            cb.onResult(cached);
            return;
        }
        ioExecutor.execute(() -> {
            OnlineInfo info = null;
            try {
                android.content.SharedPreferences sp = ctx.getSharedPreferences("riv_meta", Context.MODE_PRIVATE);
                String cachedJson = sp.getString(cacheKey, null);
                if (cachedJson != null) {
                    info = fromJson(cachedJson);
                } else {
                    boolean tv = t.season > 0 || t.episode > 0;
                    String[] order = (tv && "tvdb".equals(showSource)) ? new String[]{"tvdb", "tmdb"} : new String[]{"tmdb", "tvdb"};
                    for (String src : order) {
                        try {
                            if ("tmdb".equals(src)) {
                                if (RivPrefs.b(RivPrefs.TMDB_ON) && !RivPrefs.s(RivPrefs.TMDB_KEY, "").isEmpty()) {
                                    info = tmdb(t, arabic);
                                }
                            } else {
                                if (RivPrefs.b(RivPrefs.TVDB_ON, false) && !RivPrefs.s(RivPrefs.TVDB_KEY, "").isEmpty()) {
                                    info = tvdb(ctx, t, arabic);
                                }
                            }
                        } catch (Throwable ignored) {
                            info = null;
                        }
                        if (info != null) break;
                    }
                    if (info != null) {
                        sp.edit().putString(cacheKey, toJson(info)).apply();
                    }
                }
            } catch (Throwable ignored) {
            }
            OnlineInfo result = info;
            memCache.put(cacheKey, result);
            AndroidUtilities.runOnUIThread(() -> cb.onResult(result));
        });
    }

    private static JSONObject get(String url, java.util.Map<String, String> headers) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        try {
            return new JSONObject(readAll(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(java.io.InputStream is) throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Throwable t) {
            return s;
        }
    }

    private static String img(String path, String size) {
        if (path == null || path.isEmpty() || "null".equals(path)) return "";
        return "https://image.tmdb.org/t/p/" + size + path;
    }

    private static OnlineInfo tmdb(CleanTitle t, boolean arabic) throws Exception {
        String key = enc(RivPrefs.s(RivPrefs.TMDB_KEY, ""));
        String lang = arabic ? "ar" : "en-US";
        boolean tv = t.season > 0 || t.episode > 0;
        String url = "https://api.themoviedb.org/3/search/" + (tv ? "tv" : "multi") + "?api_key=" + key + "&query=" + enc(t.title) + "&language=" + lang;
        if (t.year > 0 && !tv) url += "&year=" + t.year;
        JSONObject res = get(url, null);
        JSONArray results = res.optJSONArray("results");
        if (results == null) return null;
        JSONObject r = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null) continue;
            if ("person".equals(o.optString("media_type"))) continue;
            r = o;
            break;
        }
        if (r == null) return null;
        String type = (tv || "tv".equals(r.optString("media_type")) || r.has("first_air_date")) ? "tv" : "movie";
        long id = r.optLong("id");
        JSONObject d;
        try {
            d = get("https://api.themoviedb.org/3/" + type + "/" + id + "?api_key=" + key + "&language=" + lang + "&append_to_response=credits", null);
        } catch (Throwable t2) {
            d = new JSONObject();
        }
        List<String> genres = new ArrayList<>();
        JSONArray ga = d.optJSONArray("genres");
        if (ga != null) for (int i = 0; i < ga.length(); i++) {
            JSONObject go = ga.optJSONObject(i);
            if (go != null) genres.add(go.optString("name"));
        }
        int runtime;
        if ("movie".equals(type)) {
            runtime = d.optInt("runtime");
        } else {
            JSONArray ert = d.optJSONArray("episode_run_time");
            runtime = (ert != null && ert.length() > 0) ? ert.optInt(0) : 0;
        }
        List<CastMember> cast = new ArrayList<>();
        JSONObject credits = d.optJSONObject("credits");
        if (credits != null) {
            JSONArray ca = credits.optJSONArray("cast");
            if (ca != null) {
                for (int i = 0; i < Math.min(12, ca.length()); i++) {
                    JSONObject co = ca.optJSONObject(i);
                    if (co != null) cast.add(new CastMember(co.optString("name"), co.optString("character"), img(co.optString("profile_path"), "w185")));
                }
            }
        }
        OnlineInfo info = new OnlineInfo();
        info.title = r.optString("title");
        if (info.title.isEmpty()) info.title = r.optString("name");
        String date = r.optString("release_date");
        if (date.isEmpty()) date = r.optString("first_air_date");
        info.year = date.length() >= 4 ? date.substring(0, 4) : date;
        info.overview = !d.optString("overview").isEmpty() ? d.optString("overview") : r.optString("overview");
        info.rating = r.optDouble("vote_average", 0.0);
        info.poster = img(r.optString("poster_path"), "w342");
        info.backdrop = img(r.optString("backdrop_path"), "w780");
        info.genres = genres;
        info.runtime = runtime;
        info.cast = cast;
        info.source = "TMDb";

        if ("tv".equals(type) && t.season > 0) {
            try {
                JSONObject s = get("https://api.themoviedb.org/3/tv/" + id + "/season/" + t.season + "?api_key=" + key + "&language=" + lang, null);
                info.season = t.season;
                info.seasonPoster = img(s.optString("poster_path"), "w342");
                JSONArray eps = s.optJSONArray("episodes");
                if (eps != null && t.episode > 0) {
                    for (int i = 0; i < eps.length(); i++) {
                        JSONObject e = eps.optJSONObject(i);
                        if (e == null) continue;
                        if (e.optInt("episode_number") == t.episode) {
                            info.episode = t.episode;
                            info.epTitle = e.optString("name");
                            info.epOverview = e.optString("overview");
                            info.epStill = img(e.optString("still_path"), "w300");
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return info;
    }

    // TheTVDB v4. The token lasts about a month; it is kept in app storage.
    private static String tvdbToken(Context ctx) throws Exception {
        android.content.SharedPreferences sp = ctx.getSharedPreferences("riv_meta_tok", Context.MODE_PRIVATE);
        String tok = sp.getString("tok", "");
        long age = System.currentTimeMillis() - sp.getLong("at", 0);
        String keyHash = String.valueOf(RivPrefs.s(RivPrefs.TVDB_KEY, "").hashCode());
        if (tok != null && !tok.isEmpty() && age < 25L * 86400_000L && keyHash.equals(sp.getString("for", ""))) {
            return tok;
        }
        HttpURLConnection c = (HttpURLConnection) new URL("https://api4.thetvdb.com/v4/login").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        JSONObject body = new JSONObject();
        body.put("apikey", RivPrefs.s(RivPrefs.TVDB_KEY, ""));
        String pin = RivPrefs.s(RivPrefs.TVDB_PIN, "");
        if (!pin.isEmpty()) body.put("pin", pin);
        try {
            java.io.OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            JSONObject data = j.optJSONObject("data");
            String newTok = data != null ? data.optString("token") : null;
            if (newTok == null || newTok.isEmpty()) return null;
            sp.edit().putString("tok", newTok).putLong("at", System.currentTimeMillis()).putString("for", keyHash).apply();
            return newTok;
        } finally {
            c.disconnect();
        }
    }

    private static OnlineInfo tvdb(Context ctx, CleanTitle t, boolean arabic) throws Exception {
        String tok = tvdbToken(ctx);
        if (tok == null) return null;
        java.util.Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + tok);
        h.put("Accept", "application/json");
        boolean tv = t.season > 0 || t.episode > 0;
        String url = "https://api4.thetvdb.com/v4/search?query=" + enc(t.title) + "&type=" + (tv ? "series" : "movie") + "&limit=1";
        if (t.year > 0 && !tv) url += "&year=" + t.year;
        JSONArray data = get(url, h).optJSONArray("data");
        JSONObject r = data != null ? data.optJSONObject(0) : null;
        if (r == null) return null;
        String id = r.optString("tvdb_id");
        if (id.isEmpty()) id = r.optString("id").replaceAll("\\D", "");

        String over = "";
        JSONObject overviews = r.optJSONObject("overviews");
        if (overviews != null) {
            over = arabic ? overviews.optString("ara") : overviews.optString("eng");
            if (over.isEmpty() && arabic) over = overviews.optString("eng");
        }
        if (over.isEmpty()) over = r.optString("overview");

        String name = "";
        JSONObject translations = r.optJSONObject("translations");
        if (translations != null) name = arabic ? translations.optString("ara") : translations.optString("eng");
        if (name.isEmpty()) name = r.optString("name");

        OnlineInfo info = new OnlineInfo();
        info.title = name;
        info.year = r.optString("year");
        info.overview = over;
        info.rating = 0.0;
        String image = r.optString("image_url");
        info.poster = image.startsWith("http") ? image : "";
        info.backdrop = "";
        info.source = "TheTVDB";

        String kind = tv ? "series" : "movies";
        try {
            JSONObject d = get("https://api4.thetvdb.com/v4/" + kind + "/" + id + "/extended", h).optJSONObject("data");
            if (d != null) {
                List<String> genres = new ArrayList<>();
                JSONArray ga = d.optJSONArray("genres");
                if (ga != null) for (int i = 0; i < ga.length(); i++) {
                    JSONObject go = ga.optJSONObject(i);
                    if (go != null) genres.add(go.optString("name"));
                }
                List<CastMember> cast = new ArrayList<>();
                JSONArray chars = d.optJSONArray("characters");
                if (chars != null) {
                    int added = 0;
                    for (int i = 0; i < chars.length() && added < 12; i++) {
                        JSONObject ch = chars.optJSONObject(i);
                        if (ch == null) continue;
                        if (ch.optInt("type") == 3 || "Actor".equals(ch.optString("peopleType"))) {
                            String photo = ch.optString("personImgURL");
                            if (photo.isEmpty()) photo = ch.optString("image");
                            cast.add(new CastMember(ch.optString("personName"), ch.optString("name"), photo));
                            added++;
                        }
                    }
                }
                String back = "";
                JSONArray arts = d.optJSONArray("artworks");
                if (arts != null) {
                    for (int i = 0; i < arts.length(); i++) {
                        JSONObject a = arts.optJSONObject(i);
                        if (a == null) continue;
                        if (a.optInt("type") == 3 || a.optInt("type") == 15) {
                            back = a.optString("image");
                            break;
                        }
                    }
                }
                String seasonPoster = "";
                JSONArray seasons = d.optJSONArray("seasons");
                if (seasons != null && t.season > 0) {
                    for (int i = 0; i < seasons.length(); i++) {
                        JSONObject s = seasons.optJSONObject(i);
                        if (s == null) continue;
                        JSONObject typeObj = s.optJSONObject("type");
                        if (s.optInt("number") == t.season && typeObj != null && "official".equals(typeObj.optString("type"))) {
                            seasonPoster = s.optString("image");
                            break;
                        }
                    }
                }
                info.genres = genres;
                info.cast = cast;
                info.backdrop = back;
                info.seasonPoster = seasonPoster;
                int rt = d.optInt("averageRuntime");
                info.runtime = rt > 0 ? rt : d.optInt("runtime");
                if (info.poster.isEmpty()) info.poster = d.optString("image");
            }
        } catch (Throwable ignored) {
        }
        if (tv && t.season > 0 && t.episode > 0) {
            try {
                JSONObject ed = get("https://api4.thetvdb.com/v4/series/" + id + "/episodes/default?season=" + t.season + "&episodeNumber=" + t.episode, h).optJSONObject("data");
                JSONArray eps = ed != null ? ed.optJSONArray("episodes") : null;
                JSONObject e = eps != null && eps.length() > 0 ? eps.optJSONObject(0) : null;
                if (e != null) {
                    info.season = t.season;
                    info.episode = t.episode;
                    info.epTitle = e.optString("name");
                    info.epOverview = e.optString("overview");
                    info.epStill = e.optString("image");
                }
            } catch (Throwable ignored) {
            }
        }
        return info;
    }

    private static String toJson(OnlineInfo i) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", i.title);
            o.put("y", i.year);
            o.put("o", i.overview);
            o.put("r", i.rating);
            o.put("p", i.poster);
            o.put("b", i.backdrop);
            o.put("g", new JSONArray(i.genres));
            o.put("rt", i.runtime);
            JSONArray cast = new JSONArray();
            for (CastMember c : i.cast) {
                JSONArray entry = new JSONArray();
                entry.put(c.name);
                entry.put(c.role);
                entry.put(c.photo);
                cast.put(entry);
            }
            o.put("c", cast);
            o.put("s", i.season);
            o.put("e", i.episode);
            o.put("et", i.epTitle);
            o.put("eo", i.epOverview);
            o.put("es", i.epStill);
            o.put("sp", i.seasonPoster);
            o.put("src", i.source);
            return o.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    private static OnlineInfo fromJson(String j) {
        try {
            JSONObject o = new JSONObject(j);
            OnlineInfo info = new OnlineInfo();
            info.title = o.optString("t");
            info.year = o.optString("y");
            info.overview = o.optString("o");
            info.rating = o.optDouble("r", 0.0);
            info.poster = o.optString("p");
            info.backdrop = o.optString("b");
            JSONArray g = o.optJSONArray("g");
            if (g != null) for (int i = 0; i < g.length(); i++) info.genres.add(g.optString(i));
            info.runtime = o.optInt("rt");
            JSONArray c = o.optJSONArray("c");
            if (c != null) {
                for (int i = 0; i < c.length(); i++) {
                    JSONArray entry = c.optJSONArray(i);
                    if (entry != null) info.cast.add(new CastMember(entry.optString(0), entry.optString(1), entry.optString(2)));
                }
            }
            info.season = o.optInt("s");
            info.episode = o.optInt("e");
            info.epTitle = o.optString("et");
            info.epOverview = o.optString("eo");
            info.epStill = o.optString("es");
            info.seasonPoster = o.optString("sp");
            info.source = o.optString("src");
            return info;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clearCache(Context ctx) {
        memCache.clear();
        try {
            ctx.getSharedPreferences("riv_meta", Context.MODE_PRIVATE).edit().clear().apply();
        } catch (Throwable ignored) {
        }
    }

    // ---------------- playback position & My List (video) ----------------

    private static android.content.SharedPreferences posPrefs(Context ctx) {
        return ctx.getSharedPreferences("riv_video_pos", Context.MODE_PRIVATE);
    }

    /** Saved playback position in ms for a video key, or 0. */
    public static long position(Context ctx, String key) {
        try {
            return posPrefs(ctx).getLong("p_" + key, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 0..1 watched fraction, for the progress bar under a poster. */
    public static float progress(Context ctx, String key) {
        try {
            long pos = posPrefs(ctx).getLong("p_" + key, 0);
            long dur = posPrefs(ctx).getLong("d_" + key, 0);
            if (dur <= 0) return 0f;
            float f = (float) pos / (float) dur;
            return Math.max(0f, Math.min(1f, f));
        } catch (Throwable t) {
            return 0f;
        }
    }

    public static void savePosition(Context ctx, String key, long positionMs, long durationMs) {
        try {
            android.content.SharedPreferences.Editor e = posPrefs(ctx).edit();
            if (durationMs > 0 && positionMs > durationMs - 15000) {
                // finished watching: drop the saved spot so it leaves "Continue watching"
                e.remove("p_" + key).remove("d_" + key);
            } else {
                e.putLong("p_" + key, positionMs).putLong("d_" + key, durationMs);
            }
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    public static boolean inMyList(String key) {
        return setContains(RivPrefs.s(RivPrefs.MY_LIST_VIDEOS, ""), key);
    }

    public static void toggleMyList(String key) {
        RivPrefs.set(RivPrefs.MY_LIST_VIDEOS, toggleInSet(RivPrefs.s(RivPrefs.MY_LIST_VIDEOS, ""), key));
    }

    public static boolean isFavoriteSong(String key) {
        return setContains(RivPrefs.s(RivPrefs.FAV_SONGS, ""), key);
    }

    public static void toggleFavoriteSong(String key) {
        RivPrefs.set(RivPrefs.FAV_SONGS, toggleInSet(RivPrefs.s(RivPrefs.FAV_SONGS, ""), key));
    }

    public static boolean isHiddenSong(String key) {
        return setContains(RivPrefs.s(RivPrefs.HIDDEN_SONGS, ""), key);
    }

    public static void hideSong(String key) {
        if (!isHiddenSong(key)) {
            RivPrefs.set(RivPrefs.HIDDEN_SONGS, toggleInSet(RivPrefs.s(RivPrefs.HIDDEN_SONGS, ""), key));
        }
    }

    private static boolean setContains(String csv, String key) {
        if (csv == null || csv.isEmpty()) return false;
        for (String p : csv.split(",")) {
            if (p.equals(key)) return true;
        }
        return false;
    }

    private static String toggleInSet(String csv, String key) {
        List<String> list = new ArrayList<>();
        if (csv != null && !csv.isEmpty()) {
            for (String p : csv.split(",")) {
                if (!p.isEmpty() && !p.equals(key)) list.add(p);
            }
        }
        if (!setContains(csv, key)) list.add(key);
        return String.join(",", list);
    }
}
