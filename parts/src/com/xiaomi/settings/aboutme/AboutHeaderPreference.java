package com.xiaomi.settings.aboutme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * About header: circular GitHub avatar + profile link.
 *
 * The avatar is fetched once from GitHub (https://github.com/<user>.png), cached in the app
 * cache dir and reused afterwards. While it is unavailable (offline / first run) the bundled
 * avatar or Neko paw icon stays visible, so the layout never jumps.
 */
public class AboutHeaderPreference extends Preference {

    static final String GITHUB_USER = "kusonekoworld-max";
    private static final String PROFILE_URL = "https://github.com/" + GITHUB_USER;
    private static final String AVATAR_URL = PROFILE_URL + ".png?size=400";
    private static final String CACHE_NAME = "github_avatar.png";
    private static final int TIMEOUT_MS = 8000;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private Bitmap mAvatar;
    private boolean mFetching;

    private int getResId(String name, String type) {
        return getContext().getResources().getIdentifier(name, type, getContext().getPackageName());
    }

    public AboutHeaderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        int layoutId = getResId("about_header", "layout");
        if (layoutId != 0) {
            setLayoutResource(layoutId);
        }
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.itemView.setClickable(false);

        int avatarId = getResId("about_avatar", "id");
        final ImageView avatar = avatarId != 0 ? (ImageView) holder.findViewById(avatarId) : null;
        if (avatar != null) {
            avatar.setClipToOutline(true);
            avatar.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            int bundledAvatarId = getResId("avatar_kusonekoworld", "drawable");
            if (bundledAvatarId != 0) {
                avatar.setImageResource(bundledAvatarId);
                avatar.setPadding(0, 0, 0, 0);
                avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }

        int githubBtnId = getResId("about_header_github", "id");
        Button github = githubBtnId != 0 ? (Button) holder.findViewById(githubBtnId) : null;
        if (github != null) {
            github.setOnClickListener(v -> getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(PROFILE_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }

        if (avatar == null) {
            return;
        }

        if (mAvatar != null) {
            showAvatar(avatar, mAvatar, false);
            return;
        }
        loadAvatar(avatar);
    }

    private void loadAvatar(ImageView target) {
        final File cache = new File(getContext().getCacheDir(), CACHE_NAME);
        if (mFetching) {
            return;
        }
        mFetching = true;
        new Thread(() -> {
            Bitmap bmp = cache.exists() ? BitmapFactory.decodeFile(cache.getAbsolutePath()) : null;
            if (bmp == null) {
                bmp = download(cache);
            }
            final Bitmap result = bmp;
            mMain.post(() -> {
                mFetching = false;
                if (result != null) {
                    mAvatar = result;
                    showAvatar(target, result, true);
                }
            });
        }, "AboutAvatar").start();
    }

    private static Bitmap download(File cache) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(AVATAR_URL).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(cache)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            Bitmap bmp = BitmapFactory.decodeFile(cache.getAbsolutePath());
            if (bmp == null) {
                cache.delete();
            }
            return bmp;
        } catch (Exception e) {
            cache.delete();
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void showAvatar(ImageView view, Bitmap bmp, boolean animate) {
        view.setPadding(0, 0, 0, 0);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        view.setImageBitmap(bmp);
        // Respect the system "remove animations" / animator-duration-scale setting.
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(300).start();
        }
    }
}
