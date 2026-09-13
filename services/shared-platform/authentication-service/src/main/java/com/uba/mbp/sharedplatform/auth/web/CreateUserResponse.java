package com.uba.mbp.sharedplatform.auth.web;

import java.util.Set;

/** {@code mfaSecret} is exposed exactly once, here — the admin hands it to the new user out-of-band for enrollment. */
public record CreateUserResponse(String username, String mfaSecret, Set<String> roles) {
}
