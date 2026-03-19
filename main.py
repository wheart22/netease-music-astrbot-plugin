"""
Netease Music Enhanced Plugin for AstrBot
- Author: NachoCrazy
- Repo: https://github.com/NachoCrazy/netease-music-astrbot-plugin
- Features: Interactive song selection, configurable song messages, lyric image rendering, and /song/url/match playback.
"""

import re
import time
import io
import base64
import aiohttp
import asyncio
import urllib.parse
from pathlib import Path
from typing import Dict, Any, Optional, List, Tuple

from astrbot.api import star, logger
from astrbot.api.event import AstrMessageEvent, filter
from astrbot.core.message.message_event_result import MessageChain
from astrbot.api.message_components import Plain, Image, Record

try:
    from PIL import Image as PILImage
    from PIL import ImageDraw, ImageFont
except ImportError:  # pragma: no cover - runtime dependency check
    PILImage = None
    ImageDraw = None
    ImageFont = None


LYRIC_IMAGE_WIDTH = 1125
LYRIC_IMAGE_MAX_HEIGHT = 16000
LYRIC_FONT_PATH = Path(__file__).resolve().parent / "assets" / "fonts" / "NotoSansSC-Regular.otf"
LYRIC_METADATA_PREFIXES = (
    "作词",
    "作曲",
    "编曲",
    "制作人",
    "监制",
    "混音",
    "母带",
    "和声",
    "录音",
    "配唱",
    "录音室",
    "企划",
    "统筹",
    "特别鸣谢",
    "出品",
    "发行",
    "封面",
    "文案",
    "吉他",
    "贝斯",
    "鼓",
    "键盘",
    "弦乐",
    "人声编辑",
    "program",
    "producer",
    "arranger",
    "composer",
    "lyricist",
)
LYRIC_PLACEHOLDER_LINES = {
    "纯音乐请欣赏",
    "此歌曲为没有填词的纯音乐请您欣赏",
    "此歌曲纯音乐请您欣赏",
    "暂无歌词",
    "伴奏",
}


# --- API Wrapper ---
class NeteaseMusicAPI:
    """
    A wrapper for the NeteaseCloudMusicApi to simplify interactions.
    Encapsulates API calls for searching, getting details, and fetching audio URLs.
    """

    def __init__(self, api_url: str, session: aiohttp.ClientSession):
        self.base_url = api_url.rstrip("/")
        self.session = session

    async def search_songs(self, keyword: str, limit: int) -> List[Dict[str, Any]]:
        """Search for songs by keyword."""
        url = f"{self.base_url}/search?keywords={urllib.parse.quote(keyword)}&limit={limit}&type=1"
        async with self.session.get(url) as r:
            r.raise_for_status()
            data = await r.json()
            return data.get("result", {}).get("songs", [])

    async def get_song_details(self, song_id: int) -> Optional[Dict[str, Any]]:
        """Get detailed information for a single song."""
        url = f"{self.base_url}/song/detail?ids={str(song_id)}"
        async with self.session.get(url) as r:
            r.raise_for_status()
            data = await r.json()
            return data["songs"][0] if data.get("songs") else None

    async def get_song_lyrics(self, song_id: int) -> Dict[str, Any]:
        """Get lyric data for a single song."""
        url = f"{self.base_url}/lyric?id={str(song_id)}"
        async with self.session.get(url) as r:
            r.raise_for_status()
            return await r.json(content_type=None)

    def _extract_match_audio_url(self, payload: Dict[str, Any]) -> Optional[str]:
        """
        Extract audio URL from /song/url/match response.
        Priority: proxyUrl > data > url
        """

        def collect_url(value: Any) -> Optional[str]:
            if isinstance(value, str) and value.strip():
                return value.strip()

            if isinstance(value, dict):
                for key in ("proxyUrl", "data", "url"):
                    nested = value.get(key)
                    if isinstance(nested, str) and nested.strip():
                        return nested.strip()

            if isinstance(value, list):
                for item in value:
                    found = collect_url(item)
                    if found:
                        return found

            return None

        for key in ("proxyUrl", "data", "url"):
            found = collect_url(payload.get(key))
            if found:
                return found

        return None

    async def get_audio_url(self, song_id: int, source: str = "") -> Optional[str]:
        """
        Get the audio stream URL via /song/url/match.
        source 留空时由后端自动匹配。
        """
        params = {"id": str(song_id)}
        if source.strip():
            params["source"] = source.strip()

        url = f"{self.base_url}/song/url/match"

        async with self.session.get(url, params=params) as r:
            try:
                data = await r.json(content_type=None)
            except Exception:
                logger.error("Netease Music plugin: /song/url/match returned non-JSON response.")
                return None

            if r.status != 200:
                logger.error(f"Netease Music plugin: /song/url/match HTTP {r.status}, response={data}")
                return None

            if data.get("code") != 200:
                logger.error(f"Netease Music plugin: /song/url/match failed, response={data}")
                return None

            return self._extract_match_audio_url(data)

    async def download_image(self, url: str) -> Optional[bytes]:
        """Download image data from a URL."""
        if not url:
            return None
        async with self.session.get(url) as r:
            if r.status == 200:
                return await r.read()
        return None


# --- Main Plugin Class ---
class Main(star.Star):
    """
    A cat-maid themed Netease Music plugin that allows users to search for,
    select, and play songs directly in the chat.
    """

    def __init__(self, context, config: Optional[Dict[str, Any]] = None):
        super().__init__(context)
        self.config = config or {}
        self.config.setdefault("api_url", "http://127.0.0.1:3000")
        self.config.setdefault("quality", "exhigh")  # 保留旧配置兼容；/song/url/match 不再使用它
        self.config.setdefault("match_source", "")   # 可选：指定匹配音源，留空表示自动匹配
        self.config.setdefault("search_limit", 5)
        self.config.setdefault("send_detail_text", True)
        self.config.setdefault("send_cover", True)
        self.config.setdefault("send_audio", True)
        self.config.setdefault("send_lyrics", False)

        self.waiting_users: Dict[str, Dict[str, Any]] = {}
        self.song_cache: Dict[str, List[Dict[str, Any]]] = {}

        self.http_session = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=20))
        self.api = NeteaseMusicAPI(self.config["api_url"], self.http_session)

        self.cleanup_task: Optional[asyncio.Task] = None

    # --- Lifecycle Hooks ---

    async def initialize(self):
        """Starts the background cleanup task when the plugin is activated."""
        self.cleanup_task = asyncio.create_task(self._periodic_cleanup())
        logger.info("Netease Music plugin: Background cleanup task started.")

    async def terminate(self):
        """Cleans up resources when the plugin is unloaded."""
        if self.cleanup_task and not self.cleanup_task.done():
            self.cleanup_task.cancel()
            try:
                await self.cleanup_task
            except asyncio.CancelledError:
                logger.info("Netease Music plugin: Background cleanup task cancelled.")

        if self.http_session and not self.http_session.closed:
            await self.http_session.close()
            logger.info("Netease Music plugin: HTTP session closed.")

    async def _periodic_cleanup(self):
        """A background task that runs periodically to clean up expired sessions."""
        while True:
            await asyncio.sleep(60)  # Run every 60 seconds
            now = time.time()
            expired_sessions = []

            for session_id, user_session in self.waiting_users.items():
                if user_session["expire"] < now:
                    expired_sessions.append((session_id, user_session["key"]))

            if expired_sessions:
                logger.info(f"Netease Music plugin: Cleaning up {len(expired_sessions)} expired session(s).")
                for session_id, cache_key in expired_sessions:
                    if session_id in self.waiting_users:
                        del self.waiting_users[session_id]
                    if cache_key in self.song_cache:
                        del self.song_cache[cache_key]

    # --- Event Handlers ---

    @filter.command("点歌", alias={"music", "听歌", "网易云"})
    async def cmd_handler(self, event: AstrMessageEvent, keyword: str = ""):
        """Handles the '/点歌' command."""
        if not keyword.strip():
            await event.send(MessageChain([Plain("主人，请告诉我您想听什么歌喵~ 例如：/点歌 Lemon")]))
            return
        await self.search_and_show(event, keyword.strip())

    @filter.regex(r"(?i)^(来.?一首|播放|听.?听|点歌|唱.?一首|来.?首)\s*([^\s].+?)(的歌|的歌曲|的音乐|歌|曲)?$")
    async def natural_language_handler(self, event: AstrMessageEvent):
        """Handles song requests in natural language."""
        match = re.search(
            r"(?i)^(来.?一首|播放|听.?听|点歌|唱.?一首|来.?首)\s*([^\s].+?)(的歌|的歌曲|的音乐|歌|曲)?$",
            event.message_str,
        )
        if match:
            keyword = match.group(2).strip()
            if keyword:
                await self.search_and_show(event, keyword)

    @filter.regex(r"^\d+$", priority=999)
    async def number_selection_handler(self, event: AstrMessageEvent):
        """Handles user's numeric choice from the search results."""
        session_id = event.get_session_id()
        if session_id not in self.waiting_users:
            return

        user_session = self.waiting_users[session_id]
        if time.time() > user_session["expire"]:
            return

        try:
            num = int(event.message_str.strip())
        except ValueError:
            return

        limit = self.config.get("search_limit", 5)
        if not (1 <= num <= limit):
            return

        event.stop_event()
        await self.play_selected_song(event, user_session["key"], num)

        if session_id in self.waiting_users:
            del self.waiting_users[session_id]

    # --- Core Logic ---

    async def search_and_show(self, event: AstrMessageEvent, keyword: str):
        """Searches for songs and displays the results to the user."""
        try:
            songs = await self.api.search_songs(keyword, self.config["search_limit"])
        except Exception as e:
            logger.error(f"Netease Music plugin: API search failed. Error: {e!s}")
            await event.send(
                MessageChain([Plain("呜喵...和音乐服务器的连接断掉了...主人，请检查一下API服务是否正常运行喵？")])
            )
            return

        if not songs:
            await event.send(MessageChain([Plain(f"对不起主人...我...我没能找到「{keyword}」这首歌喵... T_T")]))
            return

        cache_key = f"{event.get_session_id()}_{int(time.time())}"
        self.song_cache[cache_key] = songs

        response_lines = [f"主人，我为您找到了 {len(songs)} 首歌曲喵！请回复数字告诉我您想听哪一首~"]
        for i, song in enumerate(songs, 1):
            artists = " / ".join(a["name"] for a in song.get("artists", []))
            album = song.get("album", {}).get("name", "未知专辑")
            duration_ms = song.get("duration", 0)
            dur_str = f"{duration_ms // 60000}:{(duration_ms % 60000) // 1000:02d}"
            response_lines.append(f"{i}. {song['name']} - {artists} 《{album}》 [{dur_str}]")

        await event.send(MessageChain([Plain("\n".join(response_lines))]))

        self.waiting_users[event.get_session_id()] = {
            "key": cache_key,
            "expire": time.time() + 60,
        }

    async def play_selected_song(self, event: AstrMessageEvent, cache_key: str, num: int):
        """Plays the song selected by the user."""
        if cache_key not in self.song_cache:
            await event.send(MessageChain([Plain("喵呜~ 主人选择得太久了，搜索结果已经凉掉了哦，请重新点歌吧~")]))
            return

        songs = self.song_cache[cache_key]
        if not (1 <= num <= len(songs)):
            await event.send(MessageChain([Plain("主人，您输入的数字不对哦，请选择列表里的歌曲编号喵~")]))
            return

        selected_song = songs[num - 1]
        song_id = selected_song["id"]

        try:
            song_details = await self.api.get_song_details(song_id)
            if not song_details:
                raise ValueError("无法获取歌曲详细信息。")

            title = song_details.get("name", "")
            artists = " / ".join(a["name"] for a in song_details.get("ar", []))
            album = song_details.get("al", {}).get("name", "未知专辑")
            cover_url = song_details.get("al", {}).get("picUrl", "")
            duration_ms = song_details.get("dt", 0)
            dur_str = f"{duration_ms // 60000}:{(duration_ms % 60000) // 1000:02d}"
            audio_url: Optional[str] = None
            audio_notice: Optional[str] = None
            lyrics_image_base64: Optional[str] = None
            lyrics_notice: Optional[str] = None
            audio_task: Optional[asyncio.Task] = None
            lyrics_task: Optional[asyncio.Task] = None

            if self._config_enabled("send_audio", True):
                audio_task = asyncio.create_task(self._get_audio_output(song_id))

            if self._config_enabled("send_lyrics", False):
                lyrics_task = asyncio.create_task(self._get_lyrics_output(song_id, title, artists))

            if audio_task:
                audio_url, audio_notice = await audio_task

            if lyrics_task:
                lyrics_image_base64, lyrics_notice = await lyrics_task

            await self._send_song_messages(
                event,
                num,
                title,
                artists,
                album,
                dur_str,
                cover_url,
                audio_url,
                lyrics_image_base64,
                lyrics_notice,
                audio_notice,
            )

        except Exception as e:
            logger.error(f"Netease Music plugin: Failed to play song {song_id}. Error: {e!s}")
            await event.send(MessageChain([Plain("呜...获取歌曲信息的时候失败了喵...")]))
        finally:
            if cache_key in self.song_cache:
                del self.song_cache[cache_key]

    def _config_enabled(self, key: str, default: bool) -> bool:
        """Read a bool config value safely."""
        value = self.config.get(key, default)
        if isinstance(value, str):
            return value.strip().lower() in {"1", "true", "yes", "on"}
        return bool(value)

    async def _get_audio_output(self, song_id: int) -> Tuple[Optional[str], Optional[str]]:
        """Fetch audio output information without interrupting other message parts."""
        try:
            audio_url = await self.api.get_audio_url(
                song_id,
                self.config.get("match_source", "")
            )
        except Exception as e:
            logger.error(f"Netease Music plugin: Failed to get audio url for song {song_id}. Error: {e!s}")
            return None, "喵~ 这首歌的播放链接获取失败了，可能是音源服务暂时不可用呢..."

        if audio_url:
            return audio_url, None

        return None, "喵~ 这首歌暂时没有匹配到可播放链接，可能需要VIP、没有版权，或匹配音源不可用呢..."

    async def _get_lyrics_output(self, song_id: int, title: str, artists: str) -> Tuple[Optional[str], Optional[str]]:
        """Fetch lyrics and render them into a long image."""
        if PILImage is None or ImageDraw is None or ImageFont is None:
            logger.error("Netease Music plugin: Pillow is not available, cannot render lyric image.")
            return None, "喵~ 当前环境暂时无法生成歌词图片呢，请稍后再试试吧..."

        try:
            lyric_data = await self.api.get_song_lyrics(song_id)
        except Exception as e:
            logger.error(f"Netease Music plugin: Failed to get lyrics for song {song_id}. Error: {e!s}")
            return None, "喵~ 歌词接口暂时有点不稳定，没法生成歌词图片呢..."

        origin_lyrics = self._clean_lyrics_text(lyric_data.get("lrc", {}).get("lyric", ""))
        if not origin_lyrics:
            return None, "喵~ 这首歌暂时没有可发送的歌词图片呢..."

        try:
            return self._render_lyrics_image(title, artists, origin_lyrics)
        except Exception as e:
            logger.error(f"Netease Music plugin: Failed to render lyric image for song {song_id}. Error: {e!s}")
            return None, "喵~ 歌词图片渲染失败，暂时无法发送歌词图片呢..."

    def _clean_lyrics_text(self, raw_lyrics: str) -> str:
        """Remove lyric timestamps, metadata lines, and placeholder lines."""
        cleaned_lines = []
        for line in raw_lyrics.splitlines():
            cleaned = re.sub(r"\[[^\]]*\]", "", line).strip()
            cleaned = re.sub(r"\s+", " ", cleaned)
            if cleaned and not self._is_non_lyric_line(cleaned):
                cleaned_lines.append(cleaned)
        return "\n".join(cleaned_lines)

    def _is_non_lyric_line(self, line: str) -> bool:
        """Detect metadata or placeholder lines that should not appear in lyric images."""
        normalized = re.sub(r"[。！!,.，:：\s]+", "", line).strip().lower()
        if not normalized:
            return True
        if normalized in LYRIC_PLACEHOLDER_LINES:
            return True
        return normalized.startswith(LYRIC_METADATA_PREFIXES)

    def _render_lyrics_image(self, title: str, artists: str, lyrics_text: str) -> Tuple[Optional[str], Optional[str]]:
        """Render a single long lyric image with a clean reading layout."""
        if not LYRIC_FONT_PATH.is_file():
            logger.error("Netease Music plugin: lyric font file is missing.")
            return None, "喵~ 歌词图片字体资源缺失，暂时无法发送歌词图片呢..."

        try:
            title_font = ImageFont.truetype(str(LYRIC_FONT_PATH), 56)
            subtitle_font = ImageFont.truetype(str(LYRIC_FONT_PATH), 30)
            body_font = ImageFont.truetype(str(LYRIC_FONT_PATH), 36)
        except Exception as e:
            logger.error(f"Netease Music plugin: Failed to load lyric font. Error: {e!s}")
            return None, "喵~ 歌词图片字体加载失败，暂时无法发送歌词图片呢..."

        outer_padding = 40
        card_padding_x = 84
        card_padding_top = 72
        card_padding_bottom = 84
        title_spacing = 14
        subtitle_spacing = 10
        divider_gap_top = 28
        divider_gap_bottom = 36
        body_spacing = 18
        max_text_width = LYRIC_IMAGE_WIDTH - (outer_padding * 2) - (card_padding_x * 2)

        measure_canvas = PILImage.new("RGB", (LYRIC_IMAGE_WIDTH, 10), "#FFFFFF")
        measure_draw = ImageDraw.Draw(measure_canvas)

        title_lines = self._wrap_text_to_width(title, title_font, max_text_width, measure_draw)
        artist_lines = self._wrap_text_to_width(artists, subtitle_font, max_text_width, measure_draw)
        lyric_lines: List[str] = []
        for paragraph in lyrics_text.splitlines():
            lyric_lines.extend(self._wrap_text_to_width(paragraph, body_font, max_text_width, measure_draw))

        if not lyric_lines:
            return None, "喵~ 这首歌暂时没有可发送的歌词图片呢..."

        title_height = self._measure_text_block_height(title_lines, self._font_line_height(title_font), title_spacing)
        artist_height = self._measure_text_block_height(artist_lines, self._font_line_height(subtitle_font), subtitle_spacing)
        lyric_height = self._measure_text_block_height(lyric_lines, self._font_line_height(body_font), body_spacing)
        total_height = (
            outer_padding * 2
            + card_padding_top
            + title_height
            + artist_height
            + divider_gap_top
            + 1
            + divider_gap_bottom
            + lyric_height
            + card_padding_bottom
        )

        if total_height > LYRIC_IMAGE_MAX_HEIGHT:
            logger.warning(
                f"Netease Music plugin: lyric image skipped because height {total_height} exceeds limit {LYRIC_IMAGE_MAX_HEIGHT}."
            )
            return None, "喵~ 这首歌的歌词太长了，单张图片会超出安全高度，暂时无法发送歌词图片呢..."

        image = PILImage.new("RGB", (LYRIC_IMAGE_WIDTH, total_height), "#F4EEE6")
        draw = ImageDraw.Draw(image)

        card_box = (
            outer_padding,
            outer_padding,
            LYRIC_IMAGE_WIDTH - outer_padding,
            total_height - outer_padding,
        )
        draw.rounded_rectangle(card_box, radius=38, fill="#FFFDF8")

        accent_top = outer_padding + 28
        draw.rounded_rectangle(
            (outer_padding + 52, accent_top, outer_padding + 172, accent_top + 8),
            radius=4,
            fill="#D4B38A",
        )

        current_y = outer_padding + card_padding_top
        text_x = outer_padding + card_padding_x

        current_y = self._draw_text_block(draw, title_lines, title_font, text_x, current_y, "#201A17", title_spacing)
        current_y += 12
        current_y = self._draw_text_block(draw, artist_lines, subtitle_font, text_x, current_y, "#6C625A", subtitle_spacing)
        current_y += divider_gap_top

        divider_y = current_y
        draw.line(
            (text_x, divider_y, LYRIC_IMAGE_WIDTH - outer_padding - card_padding_x, divider_y),
            fill="#E4D8CB",
            width=2,
        )
        current_y = divider_y + divider_gap_bottom

        self._draw_text_block(draw, lyric_lines, body_font, text_x, current_y, "#2A2420", body_spacing)

        image_buffer = io.BytesIO()
        image.save(image_buffer, format="PNG", optimize=True)
        return base64.b64encode(image_buffer.getvalue()).decode("ascii"), None

    def _wrap_text_to_width(
        self,
        text: str,
        font: Any,
        max_width: int,
        draw: Any,
    ) -> List[str]:
        """Wrap text to the target width using character-based measurement."""
        content = text.strip()
        if not content:
            return []

        wrapped_lines: List[str] = []
        current = ""
        for char in content:
            if not current and char.isspace():
                continue
            candidate = current + char
            if current and draw.textlength(candidate, font=font) > max_width:
                wrapped_lines.append(current.rstrip())
                current = char.lstrip() if char.isspace() else char
            else:
                current = candidate

        if current.strip():
            wrapped_lines.append(current.rstrip())

        return wrapped_lines

    def _font_line_height(self, font: Any) -> int:
        """Measure font line height consistently."""
        ascent, descent = font.getmetrics()
        return ascent + descent

    def _measure_text_block_height(self, lines: List[str], line_height: int, spacing: int) -> int:
        """Measure total text block height including spacing."""
        if not lines:
            return 0
        return len(lines) * line_height + (len(lines) - 1) * spacing

    def _draw_text_block(
        self,
        draw: Any,
        lines: List[str],
        font: Any,
        x: int,
        y: int,
        fill: str,
        spacing: int,
    ) -> int:
        """Draw a wrapped text block and return the next y position."""
        line_height = self._font_line_height(font)
        current_y = y
        for index, line in enumerate(lines):
            draw.text((x, current_y), line, font=font, fill=fill)
            current_y += line_height
            if index < len(lines) - 1:
                current_y += spacing
        return current_y

    async def _send_song_messages(
        self,
        event: AstrMessageEvent,
        num: int,
        title: str,
        artists: str,
        album: str,
        dur_str: str,
        cover_url: str,
        audio_url: Optional[str],
        lyrics_image_base64: Optional[str],
        lyrics_notice: Optional[str],
        audio_notice: Optional[str],
    ):
        """Constructs and sends configured song messages."""
        source_text = self.config.get("match_source", "").strip() or "自动匹配"
        send_detail_text = self._config_enabled("send_detail_text", True)
        send_cover = self._config_enabled("send_cover", True)
        send_audio = self._config_enabled("send_audio", True)
        send_lyrics = self._config_enabled("send_lyrics", False)
        enabled_outputs = [send_detail_text, send_cover, send_audio, send_lyrics]
        sent_anything = False

        detail_text = f"""遵命，主人！为您播放第 {num} 首歌曲~

♪ 歌名：{title}
🎤 歌手：{artists}
💿 专辑：{album}
⏳ 时长：{dur_str}
🔎 匹配源：{source_text}

请主人享用喵~
"""
        info_components = []
        if send_detail_text:
            info_components.append(Plain(detail_text))

        if send_cover:
            image_data = await self.api.download_image(cover_url)
            if image_data:
                info_components.append(Image.fromBase64(base64.b64encode(image_data).decode()))

        if info_components:
            await event.send(MessageChain(info_components))
            sent_anything = True

        if send_lyrics:
            if lyrics_image_base64:
                await event.send(MessageChain([Image.fromBase64(lyrics_image_base64)]))
                sent_anything = True
            elif lyrics_notice:
                await event.send(MessageChain([Plain(lyrics_notice)]))
                sent_anything = True

        if send_audio:
            if audio_url:
                try:
                    await event.send(MessageChain([Record(url=audio_url)]))
                    sent_anything = True
                except Exception as e:
                    logger.error(f"Netease Music plugin: Failed to send audio record for {audio_url}. Error: {e!s}")
                    await event.send(
                        MessageChain(
                            [
                                Plain(
                                    "喵~ 语音消息发送超时了，当前适配器可能不太兼容远程音频。\n"
                                    f"主人可以先点这里听：{audio_url}"
                                )
                            ]
                        )
                    )
                    sent_anything = True
            elif audio_notice:
                await event.send(MessageChain([Plain(audio_notice)]))
                sent_anything = True

        if not sent_anything:
            if any(enabled_outputs):
                await event.send(
                    MessageChain([Plain("喵~ 已拿到歌曲信息，但当前没有可发送的内容，可能是封面、歌词或音频资源暂时不可用呢...")])
                )
            else:
                await event.send(
                    MessageChain([Plain("当前插件配置已关闭文字介绍、封面图、歌词和音频发送，请先在 WebUI 中开启至少一项喵~")])
                )
