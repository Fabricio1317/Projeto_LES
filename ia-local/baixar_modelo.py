"""Baixa o modelo base do ajuste fino local (Qwen2.5 1.5B Instruct, licença Apache 2.0) do Hugging Face."""
from pathlib import Path

from huggingface_hub import snapshot_download

destino = Path(__file__).parent / "modelos" / "Qwen2.5-1.5B-Instruct"
snapshot_download("Qwen/Qwen2.5-1.5B-Instruct", local_dir=destino,
                  allow_patterns=["*.json", "*.safetensors", "*.txt", "merges.txt", "vocab.json", "LICENSE"])
print("Modelo base em", destino)
