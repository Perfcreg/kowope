package com.uba.mbp.sharedplatform.auth.web;

import java.util.Set;

public record UpdateRolesRequest(Set<String> roles) {
}
