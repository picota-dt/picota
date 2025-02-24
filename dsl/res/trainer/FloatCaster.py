def is_float(txt: str) -> bool:
	try:
		float(txt)
		return True
	except ValueError:
		return False


def register_cast(register: str):
	return float(register.strip()) if is_float(register) else register.strip()