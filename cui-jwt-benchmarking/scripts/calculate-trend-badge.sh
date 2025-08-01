#!/bin/bash
# Calculate trends and create trend badge
# Usage: calculate-trend-badge.sh <tracking-file> <output-dir>

set -e

TRACKING_FILE="$1"
OUTPUT_DIR="$2"

echo "Calculating performance trends..."

# Extract last 10 scores for trend calculation
SCORES=$(jq -r '.runs[].performance.score' "$TRACKING_FILE" | tail -10)
SCORE_COUNT=$(echo "$SCORES" | wc -l)

if [ "$SCORE_COUNT" -ge 2 ]; then
  # Calculate simple trend (percentage change from first to last in the dataset)
  FIRST_SCORE=$(echo "$SCORES" | head -1)
  LAST_SCORE=$(echo "$SCORES" | tail -1)
  PERCENT_CHANGE=$(if [ "$(echo "$FIRST_SCORE == 0" | bc -l)" -eq 1 ]; then if [ "$(echo "$LAST_SCORE == 0" | bc -l)" -eq 1 ]; then echo "0"; else echo "99999"; fi; else echo "scale=2; (($LAST_SCORE - $FIRST_SCORE) / $FIRST_SCORE) * 100"; fi | bc -l)
  
  # Determine trend direction and color
  TREND_DIRECTION="stable"
  TREND_COLOR="lightgrey"
  TREND_SYMBOL="→"
  
  if [ $(echo "$PERCENT_CHANGE > 2" | bc -l) -eq 1 ]; then
    TREND_DIRECTION="improving"
    TREND_COLOR="brightgreen"
    TREND_SYMBOL="↗"
  elif [ $(echo "$PERCENT_CHANGE < -2" | bc -l) -eq 1 ]; then
    TREND_DIRECTION="declining"
    TREND_COLOR="orange"
    TREND_SYMBOL="↘"
  fi
  
  ABS_CHANGE=$(echo "$PERCENT_CHANGE" | sed 's/-//')
  FORMATTED_CHANGE=$(printf "%.1f" $ABS_CHANGE)
  
  # Create trend badge
  echo "{\"schemaVersion\":1,\"label\":\"Performance Trend\",\"message\":\"$TREND_SYMBOL $FORMATTED_CHANGE% ($TREND_DIRECTION)\",\"color\":\"$TREND_COLOR\"}" > "$OUTPUT_DIR/trend-badge.json"
  
  echo "Created trend badge: $TREND_DIRECTION ($FORMATTED_CHANGE%)"
else
  # Not enough data for trend
  echo "{\"schemaVersion\":1,\"label\":\"Performance Trend\",\"message\":\"→ Insufficient Data\",\"color\":\"lightgrey\"}" > "$OUTPUT_DIR/trend-badge.json"
  echo "Created trend badge: insufficient data"
fi