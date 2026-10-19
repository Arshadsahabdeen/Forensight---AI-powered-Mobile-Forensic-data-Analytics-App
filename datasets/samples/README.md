# Sample extraction files

All files here are **synthetic** and exist only for development and testing. They are not real evidence.

| File | Purpose |
|---|---|
| `sample-basic.xml` | Minimal file: 2 SMS, 1 contact, 1 call, 1 location. |
| `device-a-android.xml` | Timezone offsets, a local time without a zone (assumed UTC), Unix epoch milliseconds, a missing timestamp, an unparseable timestamp, and a photo with nested GPS fields. |
| `device-b-iphone.xml` | Shares phone numbers and one message with device A, so cross-device correlation, corroboration and timestamp-conflict checks have something to find. |

Import them into a case from **Case → Files & integrity → Import an extraction file**. Import A and B into the same case to see shared identifiers and corroborated records.

## Supported XML schema

The platform decodes the simple schema below. Element names are case-insensitive. Records can sit directly under the root or inside container elements such as `<messages>`. An archive (`.ufdr` or `.zip`) is accepted when it contains such an XML file. `report.xml` is preferred, otherwise the first `.xml` entry is used.

Proprietary vendor report formats (for example, Cellebrite's `report.xml` model/field structure) are **not** decoded natively.

```xml
<UFDR>
  <device>
    <manufacturer/> <brand/> <model/> <imei/> <serialNumber/> <operatingSystem/> <osVersion/>
  </device>

  <sms> | <message>        sender|from, receiver|to, message|text|body, timestamp, [timezone]
  <chat>                   app, sender, receiver, message, timestamp
  <email>                  from, to, subject, body, timestamp
  <call>                   number|phone and/or sender, receiver; direction; duration (seconds); timestamp
  <contact>                name, phone|phoneNumber|number, email
  <location>               latitude|lat, longitude|lon|lng, address|place, timestamp
  <photo> | <video>        fileName, caption, timestamp, nested latitude/longitude
  <browser> | <browserHistory>  title, url, timestamp
</UFDR>
```

### Timestamps

| Source text | Stored as | Status |
|---|---|---|
| `2026-07-14T09:20:00+05:30`, `...Z`, `...[Asia/Kolkata]` | exact UTC instant | `EXACT` |
| `1784005800000` (13 digits) / `1784005800` (10 digits) | Unix epoch, UTC | `EXACT` |
| `2026-07-14 18:45:00` with `<timezone>Asia/Kolkata</timezone>` | converted using the declared zone | `EXACT` |
| `2026-07-14 18:45:00` (no zone) | interpreted as UTC | `ASSUMED_UTC` |
| `2026-07-14` / `20260714` | 00:00 UTC on that date | `DATE_ONLY` |
| `01/08/2026 10:30` (day/month ambiguous), free text | not converted, original kept | `UNPARSEABLE` |
| element absent | — | `MISSING` |

The original timestamp text and any declared timezone are always stored with the record.
