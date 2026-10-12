# Как выпустить релиз

Релиз собирается автоматически: при публикации GitHub Release workflow
[`release.yml`](../.github/workflows/release.yml) запускает тесты, собирает оба flavor-а,
подписывает их release-ключом и прикладывает APK к этому релизу.

## Один раз: ключ подписи

Ключ подписи — это «паспорт» приложения. Если его потерять, пользователи не смогут
обновиться поверх старой версии и им придётся переустанавливать приложение.
**Сделайте резервную копию файла ключа и паролей** (например, в менеджере паролей)
и никогда не кладите их в репозиторий.

1. Создайте ключ. Нужна Java (`keytool` входит в JDK):

   ```sh
   keytool -genkeypair -v \
     -keystore youtubely-release.jks \
     -alias youtubely \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   `keytool` спросит пароль хранилища и данные владельца (имя можно указать любое).

2. Переведите файл в base64 (одной строкой):

   ```sh
   base64 -w0 youtubely-release.jks > keystore.txt     # Linux
   base64 -i youtubely-release.jks -o keystore.txt     # macOS
   ```

   В Windows (PowerShell):
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("youtubely-release.jks")) > keystore.txt`

3. В репозитории откройте **Settings → Secrets and variables → Actions → New repository secret**
   и добавьте четыре секрета:

   | Имя | Значение |
   |---|---|
   | `YOUTUBELY_KEYSTORE_BASE64` | содержимое `keystore.txt` |
   | `YOUTUBELY_KEYSTORE_PASSWORD` | пароль хранилища |
   | `YOUTUBELY_KEY_ALIAS` | `youtubely` (или ваш alias) |
   | `YOUTUBELY_KEY_PASSWORD` | пароль ключа; если не задавали отдельно — тот же, что у хранилища |

4. Удалите `keystore.txt`. Сам `.jks` храните в надёжном месте вне репозитория.

## Каждый релиз

1. Поднимите версию в `app/build.gradle.kts`:
   - `versionCode` увеличьте на 1 (Android сравнивает именно его);
   - `versionName` — номер для людей, например `0.2.0`.
2. Закоммитьте изменения в `main` (через Pull Request).
3. Опубликуйте релиз (можно с телефона):
   **Releases → Draft a new release → Choose a tag** → введите `v0.2.0` →
   **Create new tag on publish** (ветка `main`) → **Publish release**.
   APK появятся в релизе через 5–10 минут.

   Подойдёт тег `v0.2.0`, `0.2.0` или короткий `0.2` — главное, чтобы он совпадал с `versionName`.
   Из терминала то же самое: `gh release create v0.2.0 --generate-notes`.

Если сборка не прошла (например, секрет был введён с ошибкой), исправьте причину и запустите
сборку заново: **Actions → Release → Run workflow**, в поле `tag` укажите тег релиза.

Если тег не совпадает с `versionName` или секретов нет, workflow остановится с понятной ошибкой
во вкладке **Actions**.

Тестовые версии (`0.2.0-beta1`) отмечайте при публикации галочкой **Set as a pre-release**.

## Что попадает в релиз

| Файл | Описание |
|---|---|
| `YouTubely-<версия>-foss.apk` | рекомендуемая сборка, без Google Play Services |
| `YouTubely-<версия>-full.apk` | то же + (в будущем) Google Cast |
| `SHA256SUMS.txt` | контрольные суммы APK |

В описании релиза указан SHA-256 отпечаток сертификата подписи. По нему пользователи
(и Obtainium/AppVerifier) могут убедиться, что APK собран вами.

## Проверка подписи вручную

```sh
apksigner verify --print-certs YouTubely-0.2.0-foss.apk
sha256sum -c SHA256SUMS.txt
```
