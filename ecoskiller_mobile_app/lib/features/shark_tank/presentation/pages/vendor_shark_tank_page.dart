import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lucide_icons/lucide_icons.dart';
import 'package:animate_do/animate_do.dart';
import 'package:ecoskiller_mobile_app/core/theme/app_theme.dart';

class VendorSharkTankPage extends StatelessWidget {
  final Map<String, dynamic> user;
  const VendorSharkTankPage({super.key, required this.user});

  @override
  Widget build(BuildContext context) {
    bool isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? AppTheme.midnightAbyss : AppTheme.skyWhite,
      appBar: AppBar(
        title: Text('VENDOR PARTNER NODE', style: GoogleFonts.inter(fontWeight: FontWeight.w900, fontSize: 16, letterSpacing: 2)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildVendorHeader(),
            const SizedBox(height: 32),
            _buildSectionHeader('ACTIVE OFFERS'),
            const SizedBox(height: 16),
            _buildOfferList(),
            const SizedBox(height: 32),
            _buildSectionHeader('INNOVATION LEADS'),
            const SizedBox(height: 16),
            _buildLeadGrid(),
          ],
        ),
      ),
    );
  }

  Widget _buildVendorHeader() {
    return FadeInRight(
      child: Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFF00b09b), Color(0xFF96c93d)]),
          borderRadius: BorderRadius.circular(24),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(LucideIcons.package, color: Colors.white, size: 32),
            const SizedBox(height: 16),
            Text(
              'ECOSYSTEM PARTNER',
              style: GoogleFonts.inter(color: Colors.white, fontWeight: FontWeight.w900, fontSize: 18),
            ),
            const SizedBox(height: 8),
            Text(
              'Providing tools and resources to help innovators scale faster.',
              style: GoogleFonts.inter(color: Colors.white70, fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Text(
      title,
      style: GoogleFonts.inter(
        color: AppTheme.linkedinBlue.withOpacity(0.6),
        fontSize: 11,
        fontWeight: FontWeight.w900,
        letterSpacing: 2,
      ),
    );
  }

  Widget _buildOfferList() {
    return Column(
      children: [
        _OfferItem('Cloud Credits', '90% Discount for Startups', Colors.blue),
        const SizedBox(height: 12),
        _OfferItem('API Access', 'Free Tier for 1 Year', Colors.green),
      ],
    );
  }

  Widget _buildLeadGrid() {
    return GridView.count(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisCount: 2,
      crossAxisSpacing: 16,
      mainAxisSpacing: 16,
      childAspectRatio: 1.5,
      children: [
        _LeadCard('Total Claims', '45', Colors.teal),
        _LeadCard('Conversion', '12%', Colors.lime),
      ],
    );
  }
}

class _OfferItem extends StatelessWidget {
  final String title;
  final String desc;
  final Color color;
  const _OfferItem(this.title, this.desc, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: GoogleFonts.inter(fontWeight: FontWeight.bold)),
              Text(desc, style: GoogleFonts.inter(fontSize: 12, color: color)),
            ],
          ),
          const Icon(LucideIcons.tag, color: Colors.grey, size: 20),
        ],
      ),
    );
  }
}

class _LeadCard extends StatelessWidget {
  final String label;
  final String value;
  final Color color;
  const _LeadCard(this.label, this.value, this.color);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppTheme.linkedinBlue.withOpacity(0.05),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(value, style: GoogleFonts.inter(fontSize: 20, fontWeight: FontWeight.bold, color: color)),
          const SizedBox(height: 4),
          Text(label, style: GoogleFonts.inter(fontSize: 10, fontWeight: FontWeight.bold), textAlign: TextAlign.center),
        ],
      ),
    );
  }
}
