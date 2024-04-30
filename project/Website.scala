import laika.ast.{Path, SpanLink, TemplateString}
import laika.helium.Helium
import laika.helium.config.{Favicon, TextLink}
import laika.theme.config.Color

object CTTheme {
  def apply(base: Helium) =
    base.site
      .themeColors(
        primary = Color.hex("007c99"),
        secondary = Color.hex("6359ff"),
        // primaryMedium = Color.hex("0bbfbf"),
        primaryMedium = Color.hex("ffc806"),
        primaryLight = Color.hex("f7f2ea"),
        text = Color.hex("1a1a1a"),
        background = Color.hex("ffffff"),
        bgGradient = (Color.hex("ffc806"), Color.hex("f7f2ea"))
      )
      .site
      .darkMode
      .disabled
      .site
      .topNavigationBar(homeLink = TextLink.internal(Path.Root / "index.md", "fs2-queues"))
      .site
      .favIcons(Favicon.internal(Path.Root / "favicon-32x32.png", sizes = "32x32"))
      .site
      .footer(TemplateString("Copyright &copy; "), SpanLink.external("https://commercetools.com/")("commercetools"))
}
