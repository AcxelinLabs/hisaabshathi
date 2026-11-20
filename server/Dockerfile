# HisaabSaathi "Help improve" upload service. Same image on AWS and Azure.
FROM python:3.12-slim
WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY hisaab_improve ./hisaab_improve
# Not root. No access log, so no IP addresses are written anywhere.
USER 10001
ENV PORT=8080 PYTHONUNBUFFERED=1
EXPOSE 8080
CMD ["python", "-m", "hisaab_improve.app"]
