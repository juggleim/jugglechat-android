package com.juggle.im.android.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多账户加密存储。
 * <p>
 * 每条记录包含快速切换所需的会话令牌和资料，并按企业 ID 隔离展示。
 */
public final class AccountStore {
    public static final int MAX_ACCOUNT_COUNT = 5;

    private static final String TAG = "AccountStore";
    private static final String PREFS_NAME = "secure_account_store";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final Type ACCOUNT_LIST_TYPE =
            new TypeToken<ArrayList<AccountRecord>>() { }.getType();

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    /**
     * 创建多账户存储。
     *
     * @param context Android 上下文
     */
    public AccountStore(@NonNull Context context) {
        preferences = createPreferences(context.getApplicationContext());
    }

    /**
     * 读取指定企业下保存的账户，最近登录的账户排在前面。
     *
     * @param organizationId 企业 ID
     * @return 不可变账户列表
     */
    @NonNull
    public synchronized List<AccountRecord> getAccounts(@NonNull String organizationId) {
        String normalizedOrganizationId = trimToEmpty(organizationId);
        long nowMillis = System.currentTimeMillis();
        List<AccountRecord> all = readAll();
        List<AccountRecord> result = new ArrayList<>();
        boolean removedExpiredAccount = false;
        for (int index = all.size() - 1; index >= 0; index--) {
            AccountRecord account = all.get(index);
            if (!account.isSessionValid(nowMillis)) {
                all.remove(index);
                removedExpiredAccount = true;
                continue;
            }
            if (normalizedOrganizationId.equals(account.getOrganizationId())) {
                result.add(0, account);
            }
        }
        if (removedExpiredAccount) {
            writeAll(all);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 新增或更新账户记录。同一企业下以 userId 唯一，最多保留 5 个账户。
     *
     * @param account 待保存的账户记录
     */
    public synchronized void upsert(@NonNull AccountRecord account) {
        if (account.getUserId().isEmpty()
                || account.getOrganizationId().isEmpty()
                || account.getAppToken().isEmpty()
                || account.getImToken().isEmpty()) {
            throw new IllegalArgumentException("Account record is incomplete");
        }

        List<AccountRecord> all = readAll();
        removeMatching(all, account.getOrganizationId(), account.getUserId());
        all.add(0, account);

        int organizationCount = 0;
        for (int index = 0; index < all.size(); index++) {
            AccountRecord item = all.get(index);
            if (!account.getOrganizationId().equals(item.getOrganizationId())) {
                continue;
            }
            organizationCount++;
            if (organizationCount > MAX_ACCOUNT_COUNT) {
                all.remove(index);
                index--;
            }
        }
        writeAll(all);
    }

    /**
     * 同步指定账户的昵称和头像，不改变其会话令牌。
     *
     * @param organizationId 企业 ID
     * @param userId 用户 ID
     * @param nickname 最新昵称
     * @param avatar 最新头像
     */
    public synchronized void updateProfile(@NonNull String organizationId,
                                           @NonNull String userId,
                                           @Nullable String nickname,
                                           @Nullable String avatar) {
        List<AccountRecord> all = readAll();
        for (int index = 0; index < all.size(); index++) {
            AccountRecord account = all.get(index);
            if (trimToEmpty(organizationId).equals(account.getOrganizationId())
                    && trimToEmpty(userId).equals(account.getUserId())) {
                all.set(index, account.withProfile(nickname, avatar));
                writeAll(all);
                return;
            }
        }
    }

    @NonNull
    private List<AccountRecord> readAll() {
        String json = preferences.getString(KEY_ACCOUNTS, "");
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<AccountRecord> accounts = gson.fromJson(json, ACCOUNT_LIST_TYPE);
            List<AccountRecord> sanitizedAccounts = new ArrayList<>();
            if (accounts != null) {
                for (AccountRecord account : accounts) {
                    if (account != null
                            && !account.getOrganizationId().isEmpty()
                            && !account.getUserId().isEmpty()) {
                        sanitizedAccounts.add(account);
                    }
                }
            }
            return sanitizedAccounts;
        } catch (JsonSyntaxException exception) {
            Log.w(TAG, "Invalid account cache, clearing it", exception);
            preferences.edit().remove(KEY_ACCOUNTS).apply();
            return new ArrayList<>();
        }
    }

    private void writeAll(@NonNull List<AccountRecord> accounts) {
        preferences.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply();
    }

    private void removeMatching(@NonNull List<AccountRecord> accounts,
                                @NonNull String organizationId,
                                @NonNull String userId) {
        for (int index = accounts.size() - 1; index >= 0; index--) {
            AccountRecord item = accounts.get(index);
            if (organizationId.equals(item.getOrganizationId())
                    && userId.equals(item.getUserId())) {
                accounts.remove(index);
            }
        }
    }

    @NonNull
    private SharedPreferences createPreferences(@NonNull Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException exception) {
            Log.w(TAG, "Encrypted account storage unavailable, fallback to regular storage", exception);
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 可快速恢复的账户会话记录。
     */
    public static final class AccountRecord {
        private String organizationId;
        private String userId;
        private String nickname;
        private String avatar;
        private String appToken;
        private String imToken;
        private long expireAtMillis;

        private AccountRecord() {
        }

        /**
         * 创建账户会话记录。
         *
         * @param organizationId 企业 ID
         * @param userId 用户 ID
         * @param nickname 昵称
         * @param avatar 头像地址
         * @param appToken 业务令牌
         * @param imToken IM 令牌
         * @param expireAtMillis 令牌过期时间
         */
        public AccountRecord(@Nullable String organizationId,
                             @Nullable String userId,
                             @Nullable String nickname,
                             @Nullable String avatar,
                             @Nullable String appToken,
                             @Nullable String imToken,
                             long expireAtMillis) {
            this.organizationId = trimToEmpty(organizationId);
            this.userId = trimToEmpty(userId);
            this.nickname = trimToEmpty(nickname);
            this.avatar = trimToEmpty(avatar);
            this.appToken = trimToEmpty(appToken);
            this.imToken = trimToEmpty(imToken);
            this.expireAtMillis = expireAtMillis;
        }

        /**
         * 获取企业 ID。
         *
         * @return 企业 ID
         */
        @NonNull
        public String getOrganizationId() {
            return trimToEmpty(organizationId);
        }

        /**
         * 获取用户 ID。
         *
         * @return 用户 ID
         */
        @NonNull
        public String getUserId() {
            return trimToEmpty(userId);
        }

        /**
         * 获取昵称。
         *
         * @return 昵称
         */
        @NonNull
        public String getNickname() {
            return trimToEmpty(nickname);
        }

        /**
         * 获取头像地址。
         *
         * @return 头像地址
         */
        @NonNull
        public String getAvatar() {
            return trimToEmpty(avatar);
        }

        /**
         * 获取业务令牌。
         *
         * @return 业务令牌
         */
        @NonNull
        public String getAppToken() {
            return trimToEmpty(appToken);
        }

        /**
         * 获取 IM 令牌。
         *
         * @return IM 令牌
         */
        @NonNull
        public String getImToken() {
            return trimToEmpty(imToken);
        }

        /**
         * 获取令牌过期时间。
         *
         * @return 毫秒时间戳
         */
        public long getExpireAtMillis() {
            return expireAtMillis;
        }

        /**
         * 判断该账户令牌当前是否仍可用于快速切换。
         *
         * @param nowMillis 当前时间戳
         * @return true 表示令牌完整且未过期
         */
        public boolean isSessionValid(long nowMillis) {
            return !getAppToken().isEmpty()
                    && !getImToken().isEmpty()
                    && expireAtMillis > nowMillis;
        }

        @NonNull
        private AccountRecord withProfile(@Nullable String nickname, @Nullable String avatar) {
            return new AccountRecord(
                    getOrganizationId(),
                    getUserId(),
                    nickname,
                    avatar,
                    getAppToken(),
                    getImToken(),
                    expireAtMillis);
        }
    }
}
