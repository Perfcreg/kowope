package com.uba.mbp.sharedplatform.auth.web;

import java.util.Set;

public record CreateUserRequest(String username, String password, Set<String> roles) {
}
