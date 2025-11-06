# Contare Chafon Module

```bash
export iface=$(ip route get 8.8.8.8 | awk '{print $5; exit}');
echo $iface

export mac_raw="2C-AC-44-04-97-01"
export mac=$(echo $mac_raw | sed 's/-/:/g' | tr 'A-Z' 'a-z')
echo $mac
```
