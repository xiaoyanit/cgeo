package cgeo.geocaching.settings;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Hold valid or invalid credential information (login and password).
 */
public class Credentials {

    private final String username;
    private final String password;
    private final boolean isValid;

    /**
     * Create a new {@code Credentials} object. If {@code login} or {@code password} is blank, this will be considered
     * invalid login information and {@code getValid()} will return {@code false}.
     */
    public Credentials(@Nullable final String username, @Nullable final String password) {
        this.username = username;
        this.password = password;
        isValid = StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password);
    }

    /**
     * Check if the credentials are valid.
     *
     * @return {@code true} if the credentials are valid (non-blank), {@code false} otherwise
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Check if the credentials are invalid.
     *
     * @return {@code false} if the credentials are valid (non-blank), {@code true} otherwise
     */
    public boolean isInvalid() {
        return !isValid;
    }

    /**
     * Stored username information.
     *
     * @return the username if valid
     * @throws IllegalArgumentException
     *         if credentials are invalid
     */
    @NonNull
    public String getUsername() {
        guard();
        return username;
    }

    /**
     * Stored username information. The validity of the credentials is not checked.
     *
     * @return the username, which may be an empty string
     */
    @NonNull
    String getUsernameRaw() {
        return StringUtils.defaultIfBlank(username, StringUtils.EMPTY);
    }

    /**
     * Stored password information.
     *
     * @return the password if valid
     * @throws IllegalArgumentException
     *         if credentials are invalid
     */
    @NonNull
    public String getPassword() {
        guard();
        return password;
    }

    /**
     * Stored password information. The validity of the credentials is not checked.
     *
     * @return the password, which may be an empty string
     */
    @NonNull
    String getPasswordRaw() {
        return StringUtils.defaultIfBlank(password, StringUtils.EMPTY);
    }

    private void guard() {
        if (!isValid) {
            throw new IllegalArgumentException("credentials are not valid");
        }
    }
}
