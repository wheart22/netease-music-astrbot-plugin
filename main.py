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

    async def get_audio_url(self, song_id: int, quality: str) -> Optional[str]:
        """
        Get the audio stream URL for a song with automatic quality fallback.
        Uses /song/url/match instead of /song/url/v1.
        """
        qualities_to_try = list(dict.fromkeys([quality, "exhigh", "higher", "standard"]))

        for q in qualities_to_try:
            url = f"{self.base_url}/song/url/match?id={str(song_id)}&level={q}"
            async with self.session.get(url) as r:
                r.raise_for_status()
                data = await r.json()

                if isinstance(data.get("data"), list):
                    audio_info = data.get("data", [{}])[0]
                    if audio_info.get("url"):
                        return audio_info["url"]

                elif isinstance(data.get("data"), dict):
                    if data["data"].get("url"):
                        return data["data"]["url"]

                elif data.get("url"):
                    return data["url"]

        return None

    async def download_image(self, url: str) -> Optional[bytes]:
        """Download image data from a URL."""
        if not url:
            return None
        async with self.session.get(url) as r:
            if r.status == 200:
                return await r.read()
        return None
