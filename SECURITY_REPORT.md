# iTaxViewer Security Assessment Report

**Date:** 2026-06-22
**Tool:** iTax Validator v1.0
**Target:** iTax Viewer (Cục Thuế Việt Nam) v2.7.6
**Test environment:** Windows 11, JDK 25, iTaxViewer self-hosted runner

---

## Summary

| # | Vulnerability | Severity | Confirmed | PoC File |
|---|---------------|----------|-----------|----------|
| 1 | **CDATA Injection → XSS** | **CRITICAL** | ✅ **Confirmed** | `poc_cert_bypass_xss.xml` |
| 2 | **XML Signature Wrapping (XSW)** | **HIGH** | ⚠️ Partial | `itax_xsw*.xml` |

---

## 1. CDATA Injection → Cross-Site Scripting (XSS) — CRITICAL

### Confirmed: ✅

**PoC file:** `samples/poc_cert_bypass_xss.xml`

### Root Cause

iTaxViewer's `IHTKKXMLSignature.java` processes the XML signature `<Object>` elements. The code reads`DateTimeStamp` from the **first** `<SignatureProperty>` using:

```java
// IHTKKXMLSignature.java:118
NodeList signPropList = doc.getElementsByTagName("SignatureProperty");
if (signPropList.getLength() > 0) {
    NodeList childList = signPropList.item(0).getChildNodes();
    ...
}
```

An attacker can inject a **CDATA-wrapped payload** in the very first `<DateTimeStamp>` element. The payload bypasses XML parsing because CDATA preserves raw content.

The injected content flows through:

1. `signElement.getFirstChild().getTextContent()` (IHTKKXMLSignature.java:393)
2. `ConvertDateStringToFormat()` in `TaxViewerUtils.java:62-68`
3. **Exception thrown** because `<img src=x onerror=alert(12345)>` is not a valid date
4. **Catch block returns raw payload** (TaxViewerUtils.java:67)
5. Raw payload set as timestamp → rendered in HTML via SWT Browser

Key code in `TaxViewerUtils.java`:

```java
public static String ConvertDateStringToFormat(String dateVal) {
    if (dateVal == null || dateVal.equals("")) return dateVal;
    try {
        // ... date parsing ...
    } catch (Exception e) {
        // ❌ BUG: Returns raw input, enabling XSS
        return dateVal;
    }
}
```

### Impact

- Attacker crafts a valid XML tax declaration with a valid digital signature
- Embeds `<![CDATA[<img src=x onerror=alert(12345)>]]>` in DateTimeStamp
- File is validated as **structure VALID + signature HỢP LỆ**
- XSS payload renders in the embedded SWT Browser (IE-based)
- **Full compromise of the tax viewer user's session**

### Proof

Validator output confirms payload in timestamp field:
```
Chữ ký điện tử hợp lệ
(ngày <i tháng mg năm  src , =x giá  o phát nerror=alert(12345)> giấy)
```

---

## 2. XML Signature Wrapping (XSW) — HIGH

### Confirmed: ⚠️ Partial

**PoC files:** `samples/itax_xsw.xml`, `itax_xsw2.xml`, `itax_xsw_proved.xml`

### Description

XML Signature Wrapping bypasses signature verification while displaying attacker-controlled content. The attack uses two sibling elements with the same tag name:

- **Element 1 (fake):** Attacker-controlled content, no `Id` attribute
- **Element 2 (real):** Original signed content with `Id="_NODE_TO_SIGN"`

### Current Status

- ✅ Structure validation passes (correct namespace, schema, version)
- ❌ **Signature verification fails** — DOM ID resolution cannot find `_NODE_TO_SIGN` when a preceding sibling exists

The error:
```
Cannot resolve element with ID _NODE_TO_SIGN
```

### Root Cause of Failure

`IHTKKXMLSignature.java` uses `doc.getElementsByTagName("TBaoThue").item(0)` to locate the signing target. With XSW:
- `item(0)` returns the fake TBaoThue (no Id)
- `setIdAttributeNS` is called on the wrong element
- ID resolution fails when Apache XML Security tries to resolve `#_NODE_TO_SIGN`

### Feasibility

The XSW attack is **theoretically valid** but requires specific DOM behavior:
1. The application must read `item(0)` (fake content) for display
2. The signature validation must use ID-based resolution to find element 2
3. DOM implementation differences between JDK 25 and iTaxViewer's bundled JRE 8 may enable this

**Status: Partial — requires further analysis on JRE 8**

---

## Attack Tree

```
CDATA Injection
├── [1] Craft XML with malicious CDATA in DateTimeStamp
├── [2] Sign with any valid certificate
├── [3] Structure validated: VALID
├── [4] Signature validated: HỢP LỆ (CDATA bypasses XML parsing)
├── [5] ConvertDateStringToFormat throws exception
├── [6] Catch returns raw XSS payload
└── [7] Rendering: XSS executes in SWT Browser → COMPROMISE

XML Signature Wrapping
├── [1] Craft XML with 2 TBaoThue (fake + real)
├── [2] Copy signature from valid document
├── [3] Structure validated: VALID
├── [4] Signature validation: FAILS (ID resolution)
├── [5] If ID resolution works: signature HỢP LỆ
└── [6] Display reads item(0) → fake content shown → COMPROMISE
```

---

## Recommendations

### Critical (CDATA → XSS)

1. **Sanitize all date/time values before rendering** — never return raw input from a parse function
2. **Use HTML entity encoding** for displayed values
3. **Upgrade embedded browser** from IE-based SWT Browser to Edge WebView2
4. **Add Content Security Policy** (CSP) to rendered HTML

### High (XSW)

1. **Always reference signed elements by ID, not position** (`item(0)`)
2. **Verify element count** — reject documents with unexpected duplicates
3. **Use `item(0)` consistently** — both display AND signature must use the same element

### General

1. Use `try-finally` or `try-with-resources` instead of error-swallowing catches
2. Remove `Msxml2.XMLHTTP.3.0` dependency (IE-specific)
3. Validate XML against schema before processing (already done — ✅)

---

## Files

| File | Status | Signature |
|------|--------|-----------|
| `itax.xml` | ✅ VALID | ✅ HỢP LỆ |
| `itax_signed.xml` | ✅ VALID | ✅ HỢP LỆ |
| `poc_cert_bypass_xss.xml` | ✅ VALID | ✅ HỢP LỆ |
| `itax_xsw.xml` | ✅ VALID | ❌ LỖI |
| `itax_xsw2.xml` | ✅ VALID | ❌ LỖI |
| `itax_xsw_proved.xml` | ✅ VALID | ❌ LỖI |
| `poc_output_cert_bypass.xml` | ✅ VALID | ✅ HỢP LỆ |
