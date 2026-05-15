import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class RoundCard extends StatelessWidget {
  final String title;
  final String domain;
  final String prizePool;
  final String deadline;
  final Color color;

  const RoundCard({
    super.key,
    required this.title,
    required this.domain,
    required this.prizePool,
    required this.deadline,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isDark ? Colors.white.withOpacity(0.05) : Colors.white,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withOpacity(0.2)),
        boxShadow: [
          if (!isDark)
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 10,
              offset: const Offset(0, 5),
            ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: color.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  domain.toUpperCase(),
                  style: GoogleFonts.inter(
                    color: color,
                    fontSize: 10,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 1,
                  ),
                ),
              ),
              Row(
                children: [
                  const Icon(LucideIcons.clock, size: 14, color: Colors.grey),
                  const SizedBox(width: 4),
                  Text(
                    deadline,
                    style: GoogleFonts.inter(fontSize: 12, color: Colors.grey),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: GoogleFonts.inter(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: isDark ? Colors.white : AppTheme.deepOcean,
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'PRIZE POOL',
                    style: GoogleFonts.inter(fontSize: 10, color: Colors.grey, letterSpacing: 1),
                  ),
                  Text(
                    prizePool,
                    style: GoogleFonts.inter(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                      color: AppTheme.linkedinBlue,
                    ),
                  ),
                ],
              ),
              const Spacer(),
              const Icon(LucideIcons.chevronRight, color: Colors.grey),
            ],
          ),
        ],
      ),
    );
  }
}
