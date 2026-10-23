package com.peykich.soraraw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRulesTest {
    @Test
    fun sameDomainOpensInApp() {
        assertTrue(SiteRules.isInternalHost("soraraw.com"))
        assertTrue(SiteRules.isInternalHost("SORARAW.COM"))
        assertTrue(SiteRules.isInternalHost("www.soraraw.com"))
        assertTrue(SiteRules.isInternalHost("soraraw.com."))
    }

    @Test
    fun otherDomainsOpenInChrome() {
        assertFalse(SiteRules.isInternalHost(null))
        assertFalse(SiteRules.isInternalHost(""))
        assertFalse(SiteRules.isInternalHost("example.com"))
        // 名前の一部が同じだけのドメインは外部扱い
        assertFalse(SiteRules.isInternalHost("evilsoraraw.com"))
        assertFalse(SiteRules.isInternalHost("soraraw.com.evil.example"))
    }
}
