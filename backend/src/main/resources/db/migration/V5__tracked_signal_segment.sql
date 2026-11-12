-- Market-cap segment (LARGE / MID / SMALL / OTHER) of each paper trade, to compare results by segment.
ALTER TABLE tracked_signal ADD COLUMN segment VARCHAR(16);
