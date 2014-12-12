package com.criticollab.microdata.support;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by ses on 12/12/14.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ManifestPath {
    String value();
}
