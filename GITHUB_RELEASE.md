# GitHub Actions и Release

Репозиторий: https://github.com/gasada-dev/Minecraft-wacom-integration

Push в main, pull request или Run workflow запускает сборку на Ubuntu и Windows
с JDK 25 и Gradle Wrapper. Обе платформы выполняют `clean build`, включая self-test,
проверку методов Minecraft и конечного JAR. Артефакты: `artmap-pen-ubuntu-24.04`
и `artmap-pen-windows-latest`; внутри JAR и SHA256SUMS.

После успешного прогона main тег `v` + `mod_version` запускает те же проверки.
Только после их успеха workflow создаёт **draft prerelease** с JAR из Ubuntu и
контрольной суммой; текст берётся из RELEASE_NOTES.md. Существующие assets с такими
именами заменяются при повторном прогоне. Workflow не публикует черновик автоматически.

```powershell
git tag v0.2.0-alpha.1
git push origin v0.2.0-alpha.1
```

После ручной проверки из TESTING.md владелец может опубликовать подготовленный
черновик на странице Releases. Успех CI подтверждает сборку и автоматические проверки,
а работоспособность планшета/ArtMap подтверждается отдельно.
