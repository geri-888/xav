# XAntiVPN

XAntiVPN egy Minecraft anti VPN / proxy / datacenter ellenorzo plugin. Csatlakozas elott lekeri a jatekos IP cimet, elkuldi az ipapi.is API-nak, es ha tiltott tipus jon vissza, nem engedi fel a szerverre.


## Platformok

- Bukkit / Spigot / Paper: `XAntiVPN-Bukkit-26.5.jar`
- BungeeCord: `XAntiVPN-Bungee-26.5.jar`
- Velocity: `XAntiVPN-Velocity-26.5.jar`

## Hogyan mukodik

Amikor valaki csatlakozik, a plugin megnezi az IP cimet az ipapi.is API-val. Ezeket figyeli:

- `is_bogon`
- `is_mobile`
- `is_satellite`
- `is_crawler`
- `is_datacenter`
- `is_tor`
- `is_proxy`
- `is_vpn`
- `is_abuser`

Ha ezek kozul barmelyik tiltott es `true`, a jatekos kickelve lesz. A kick uzenet es a hibakodok configbol szerkeszthetok.

Az IPAPI valaszt a plugin egy ideig RAM-ban cache-eli, alapbol 30 percig. Ez azert van, hogy ne kerdezze le ugyanazt az IP-t minden masodpercben, ha valaki ujra probalkozik.

Fontos: a cache IP cim alapjan mukodik. Ha valaki lelep es ugyanazzal az IP-vel visszajon, nem tudja kijatszani, mert ugyanazt az eredmenyt kapja vissza cache-bol. Ha IP-t valt, az mar masik IP, arra uj API lekeres tortenik.

Az utoljara latott jatekos IP-k adatbazisban vannak tarolva, ez kell az `/xav ip` es `/xav alt` parancsokhoz. Ez nem whitelist, nem bypass, csak elozo IP nyilvantartas.

## Adatbazis

Alapbol H2 adatbazist hasznal, hasonlo stilusban, mint sok mas Minecraft plugin:

```yaml
storage:
  type: "h2"
```

Ha SQLite kell:

```yaml
storage:
  type: "sqlite"
```

H2 fajl: `xavpn.mv.db`

SQLite fajl: `xavpn.sqlite3`

A regi `whitelist.txt` es `players.properties` tartalmat beolvassa az adatbazisba, ha megtalalja.

## Parancsok

- `/xav kivetel <jatekosnev>` - jatekos hozzaadasa a whitelisthez
- `/xav eltavolit <jatekosnev>` - jatekos eltavolitasa a whitelistrol
- `/xav ip <jatekos>` - IP, ISP es aktiv kodok lekerdezese
- `/xav alt <jatekos|ip>` - ismert azonos IP-s jatekosok listazasa
- `/xav reload` - config es adatbazis kapcsolat ujratoltese restart nelkul

## Jogok

- `xav.whitelist` - `/xav kivetel` es `/xav eltavolit`
- `xav.kivetel` - automatikus anti VPN bypass Bukkit/Paper/Spigot alatt
- `xav.mod` - `/xav ip`, `/xav alt`, blokkolasi ertesites es update ertesites
- `xav.reload` - `/xav reload`

Velocityn, BungeeCord-on es Bukkit oldalon is jogra vannak korlatozva a parancsok.

## Moderator ertesites

Ha valakit blokkol az AntiVPN, a `xav.mod` joggal rendelkezok latnak egy uzenetet, peldaul:

```text
mr_fly csatlakozasa blokkolva lett az AntiVPN altal (VPN, Proxy, Datacenter)
```

Itt nem hibakodot ir, hanem a talalt tipusokat.

## Update ertesites

A plugin a GitHub latest release alapjan nez frissitest:

```text
https://github.com/geri-888/xav/releases/latest
```

Ha a latest release verzioja ujabb, mint a plugin jelenlegi verzioja, akkor a `xav.mod` jogos jatekos kap ertesitest.

Velocityn ez 20 masodperccel belepes utan jelenik meg, hogy auth utan mar nagy esellyel a lobbyban lassa a jatekos.

## Config

Elso inditaskor letrejon a `config.yml`. Ebben allithato:

- API kulcs
- API endpoint
- timeout
- cache ido
- fail-closed viselkedes
- tarolas tipusa
- tiltott flag-ek
- hibakodok
- kick uzenet
- moderator ertesites szovege

Alap API forma:

```text
https://api.ipapi.is/?q={ip}&key={key}
```

## Build

```text
gradle clean build --no-daemon --stacktrace
```

Jarok build utan:

- `bukkit/build/libs/XAntiVPN-Bukkit-26.5.jar`
- `bungee/build/libs/XAntiVPN-Bungee-26.5.jar`
- `velocity/build/libs/XAntiVPN-Velocity-26.5.jar`
