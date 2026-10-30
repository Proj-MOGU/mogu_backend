package com.mogu.auth.application;

import com.mogu.auth.domain.DiHash;

public interface DiHasher {
    DiHash hash(String di);
}
